package sp.ax.nfctags

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class NFCTagsReceiversTest {
    @Config(application = MockApplication::class)
    @Test
    fun adapterTest() {
        runTest(timeout = 6.seconds) {
            val context: Context = RuntimeEnvironment.getApplication()
            val states = listOf(
                true,
                false,
                true,
            )
            val job = launch(CoroutineName("adapter")) {
                NFCTagsReceivers.adapter(context = context).take(states.size).collectIndexed { index, actual ->
                    if (index !in states.indices) error("Index $index is unexpected!")
                    assertEquals(states[index], actual)
                }
            }
            delay(1.seconds)
            val intent = Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            intent.setPackage(context.packageName)
            listOf(
                NfcAdapter.STATE_TURNING_ON,
                NfcAdapter.STATE_ON,
                NfcAdapter.STATE_TURNING_OFF,
                NfcAdapter.STATE_OFF,
                NfcAdapter.STATE_TURNING_ON,
                NfcAdapter.STATE_ON,
            ).forEach { state ->
                intent.putExtra(NfcAdapter.EXTRA_ADAPTER_STATE, state)
                context.sendBroadcast(intent)
            }
            job.join()
        }
    }
}
