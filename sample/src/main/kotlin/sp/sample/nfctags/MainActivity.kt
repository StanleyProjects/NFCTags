package sp.sample.nfctags

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
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

    private val receivers = object : BroadcastReceiver() {
        private fun onReceive(intent: Intent) {
            when (intent.action) {
                NfcAdapter.ACTION_ADAPTER_STATE_CHANGED -> {
                    val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1)
                    when (state) {
                        NfcAdapter.STATE_ON -> {
                            println("[MainActivity]:receivers:onReceive($state)")
                        }
                        else -> {
                            println("[MainActivity]:receivers:onReceive($state)")
                        }
                    }
                }
            }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            println("[MainActivity]:receivers:onReceive(${context?.hashCode()} ${intent?.action} ${intent?.extras?.keySet()?.toList()})")
            if (intent == null) return
            onReceive(intent = intent)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        val view = ComposeView(this)
//        setContentView(view)
//        view.setContent {
//            MainScreen()
//        }
        _adapter = NfcAdapter.getDefaultAdapter(this)
        val filters = IntentFilter()
        filters.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        registerReceiver(
            receivers,
            filters,
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onPause() {
        super.onPause()
        val adapter = _adapter ?: TODO("No adapter!")
        val activity: Activity = this
//        adapter.disableForegroundDispatch(this)
        adapter.disableReaderMode(activity)
    }

    override fun onResume() {
        super.onResume()
        val adapter = _adapter ?: TODO("No adapter!")
        val context: Context = this
        val activity: Activity = this
//        val intent = Intent(context, activity::class.java)
//        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
//        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_MUTABLE)
        //
//        val intent = Intent("foobarbaz")
//        intent.setPackage(packageName)
//        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE)
        //
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        val techLists = arrayOf<Array<String>>()
//        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techLists)
        val callback = NfcAdapter.ReaderCallback { tag ->
            val tt = IsoDep.get(tag)
            println("[MainActivity]:tag: ${tt?.tag?.id?.toHEX()}") // todo
        }
//        val flags = NfcAdapter.FLAG_READER_NFC_A or
//            NfcAdapter.FLAG_READER_NFC_B or
//            NfcAdapter.FLAG_READER_NFC_F or
//            NfcAdapter.FLAG_READER_NFC_V or
//            NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
//        val flags = 0
//        val flags = NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
//        val flags = NfcAdapter.FLAG_READER_NFC_A
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        adapter.enableReaderMode(activity, callback, flags, null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
//        val adapter = _adapter ?: TODO("No adapter!")
//        when (intent.action) {
//            NfcAdapter.ACTION_TAG_DISCOVERED -> {
//                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
//                val tt = IsoDep.get(tag) ?: return
//                println("[MainActivity]:tag: ${tt.tag.id.toHEX()}") // todo
//            }
//            else -> return
//        }
    }
}
