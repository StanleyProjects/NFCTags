package sp.ax.nfctags

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import org.junit.Assert.assertTrue
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
                    .create()
                    .visible()
                    .start()
                    .resume()
                val activity = controller.get()
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
                                    1 -> {
                                        val currentState = activity.lifecycle.currentState
                                        assertTrue("Current state: $currentState", currentState >= Lifecycle.State.RESUMED)
                                        assertEquals(NFCTags.State.Searching, state)
                                    }
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            tags.start(activity = activity)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun startStartedTest() {
        runTest(timeout = 6.seconds) {
            onNfcAdapter { _ ->
                val controller = Robolectric.buildActivity(MockActivity::class.java)
                    .create()
                    .visible()
                    .start()
                val activity = controller.get()
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
                                    1 -> {
                                        val currentState = activity.lifecycle.currentState
                                        assertTrue("Current state: $currentState", currentState < Lifecycle.State.RESUMED)
                                        assertEquals(NFCTags.State.Waiting, state)
                                    }
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            tags.start(activity = activity)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun startStopTest() {
        runTest(timeout = 6.seconds) {
            onNfcAdapter { _ ->
                val controller = Robolectric.buildActivity(MockActivity::class.java)
                    .create()
                    .visible()
                    .start()
                    .resume()
                val activity = controller.get()
                onRealNFCTags(testScheduler) { tags ->
                    launch(CoroutineName("events")) {
                        tags.events.take(1).collect { event ->
                            error("Event $event is unexpected!")
                        }
                    }.cancel {
                        launch(CoroutineName("states")) {
                            tags.states.take(3).collectIndexed { index, state ->
                                when (index) {
                                    0 -> assertEquals(NFCTags.State.Stopped, state)
                                    1 -> {
                                        val currentState = activity.lifecycle.currentState
                                        assertTrue("Current state: $currentState", currentState >= Lifecycle.State.RESUMED)
                                        assertEquals(NFCTags.State.Searching, state)
                                        delay(1.seconds)
                                        tags.stop()
                                    }
                                    2 -> assertEquals(NFCTags.State.Stopped, state)
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            tags.start(activity = activity)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun waitingTest() {
        runTest(timeout = 6.seconds) {
            onNfcAdapter { _ ->
                val controller = Robolectric.buildActivity(MockActivity::class.java)
                    .create()
                    .visible()
                    .start()
                    .resume()
                val activity = controller.get()
                onRealNFCTags(testScheduler) { tags ->
                    launch(CoroutineName("events")) {
                        tags.events.take(1).collect { event ->
                            error("Event $event is unexpected!")
                        }
                    }.cancel {
                        launch(CoroutineName("states")) {
                            tags.states.take(5).collectIndexed { index, state ->
                                when (index) {
                                    0 -> assertEquals(NFCTags.State.Stopped, state)
                                    1 -> {
                                        val currentState = activity.lifecycle.currentState
                                        assertTrue("Current state: $currentState", currentState >= Lifecycle.State.RESUMED)
                                        assertEquals(NFCTags.State.Searching, state)
                                        delay(1.seconds)
                                        controller.pause()
                                    }
                                    2 -> {
                                        val currentState = activity.lifecycle.currentState
                                        assertTrue("Current state: $currentState", currentState < Lifecycle.State.RESUMED)
                                        assertEquals(NFCTags.State.Waiting, state)
                                        delay(1.seconds)
                                        controller.resume()
                                    }
                                    3 -> {
                                        val currentState = activity.lifecycle.currentState
                                        assertTrue("Current state: $currentState", currentState >= Lifecycle.State.RESUMED)
                                        assertEquals(NFCTags.State.Searching, state)
                                        delay(1.seconds)
                                        tags.stop()
                                    }
                                    4 -> assertEquals(NFCTags.State.Stopped, state)
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            tags.start(activity = activity)
                        }
                    }
                }
            }
        }
    }
}
