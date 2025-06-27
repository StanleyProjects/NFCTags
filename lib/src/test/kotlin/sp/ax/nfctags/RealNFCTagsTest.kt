package sp.ax.nfctags

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

    @Test
    fun startTest() {
        runTest(timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            ShadowNfcAdapter.setNfcHardwareExists(true)
            Shadows.shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_NFC, true)
            val adapter = NfcAdapter.getDefaultAdapter(application) ?: TODO("RealNFCTagsTest:startTest:no adapter!")
            Shadows.shadowOf(adapter).setEnabled(true)
            val main: CoroutineContext = StandardTestDispatcher(testScheduler, "real:tags:main")
            val default: CoroutineContext = StandardTestDispatcher(testScheduler, "real:tags:default")
            val job = SupervisorJob()
            val tags = RealNFCTags(
                coroutineScope = CoroutineScope(main + job),
                default = default + job,
            )
            assertEquals("before start", NFCTags.State.Stopped, tags.states.value)
            val controller = Robolectric.buildActivity(MockActivity::class.java)
            val activity = controller.get()
            controller.start()
            controller.resume()
            launch(CoroutineName("before start")) {
                tags.states.takeWhile { state ->
                    state != NFCTags.State.Searching
                }.collect()
            }.join {
                tags.start(activity = activity)
            }
            TODO("RealNFCTagsTest:startTest")
            job.cancel()
        }
    }
}
