package sp.ax.nfctags

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object NFCTagsReceivers {
    fun adapter(context: Context): Flow<Boolean> {
        return callbackFlow {
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val state = intent?.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1)
                    when (state) {
                        NfcAdapter.STATE_ON -> trySend(true)
                        NfcAdapter.STATE_TURNING_OFF -> trySend(false)
                    }
                }
            }
            val filters = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receivers,
                    filters,
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                context.registerReceiver(receivers, filters)
            }
            awaitClose {
                context.unregisterReceiver(receivers)
            }
        }
    }
}
