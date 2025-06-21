package sp.sample.nfctags

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import sp.kx.bytes.toHEX

internal class MainActivity : ComponentActivity() {
    private var _adapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        val view = ComposeView(this)
//        setContentView(view)
//        view.setContent {
//            MainScreen()
//        }
        _adapter = NfcAdapter.getDefaultAdapter(this)
    }

    override fun onPause() {
        super.onPause()
        val adapter = _adapter ?: TODO("No adapter!")
        adapter.disableForegroundDispatch(this)
    }

    override fun onResume() {
        super.onResume()
        val adapter = _adapter ?: TODO("No adapter!")
        val context: Context = this
        val activity: Activity = this
        val intent = Intent(context, activity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_MUTABLE)
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        val techLists = arrayOf<Array<String>>()
        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techLists)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val adapter = _adapter ?: TODO("No adapter!")
        when (intent.action) {
            NfcAdapter.ACTION_TAG_DISCOVERED -> {
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
                val tt = IsoDep.get(tag) ?: return
                println("[MainActivity]:tag: ${tt.tag.id.toHEX()}") // todo
            }
            else -> return
        }
    }
}
