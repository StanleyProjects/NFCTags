package sp.ax.nfctags

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNfcAdapter
import org.robolectric.shadows.ShadowServiceManager
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class RealNFCTagsTest {
    @Test
    fun stoppedTest() {
        runTest(timeout = 6.seconds) {
            val main: CoroutineContext = StandardTestDispatcher(testScheduler, "real:tags:main")
            val default: CoroutineContext = StandardTestDispatcher(testScheduler, "real:tags:default")
            val job = SupervisorJob()
            val tags = RealNFCTags(
                coroutineScope = CoroutineScope(main + job),
                default = default + job,
            )
            assertEquals("before start", NFCTags.State.Stopped, tags.states.value)
            job.cancel()
        }
    }

    private suspend fun onRealNFCTags(
        scheduler: TestCoroutineScheduler,
        block: suspend (NFCTags) -> Unit,
    ) {
        val job = SupervisorJob()
        val main: CoroutineContext = StandardTestDispatcher(scheduler, "real:tags:main")
        val default: CoroutineContext = StandardTestDispatcher(scheduler, "real:tags:default")
        val tags = RealNFCTags(
            coroutineScope = CoroutineScope(main + job),
            default = default + job,
        )
        block(tags)
        job.cancel()
    }

    private suspend fun onNfcAdapter(
        context: Context = RuntimeEnvironment.getApplication(),
        isEnabled: Boolean = true,
        block: suspend (NfcAdapter) -> Unit,
    ) {
        ShadowNfcAdapter.setNfcHardwareExists(true)
        Shadows.shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_NFC, true)
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: TODO("RealNFCTagsTest:onNfcAdapter:no adapter!")
        Shadows.shadowOf(adapter).setEnabled(isEnabled)
        block(adapter)
    }

    @Test
    fun startTest() {
        runTest(timeout = 6.seconds) {
            onNfcAdapter { _ ->
                val controller = Robolectric.buildActivity(MockActivity::class.java)
                    .start()
                    .resume()
                onRealNFCTags(testScheduler) { tags ->
                    launch(CoroutineName("events")) {
                        tags.events.take(1).collect { event ->
                            error("Event $event is unexpected!")
                        }
                    }.cancel {
                        launch(CoroutineName("states")) {
                            tags.states.take(2).collectIndexed { index, state ->
                                when (index) {
                                    0 -> assertEquals(NFCTags.State.Stopped, state)
                                    1 -> assertEquals(NFCTags.State.Searching, state)
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            tags.start(activity = controller.get())
                        }
                    }
                }
            }
        }
    }
}
