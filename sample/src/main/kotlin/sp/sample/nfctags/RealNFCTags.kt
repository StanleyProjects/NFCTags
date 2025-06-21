package sp.sample.nfctags

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sp.ax.nfctags.NFCTags
import kotlin.coroutines.CoroutineContext

class RealNFCTags(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
) : NFCTags {
    private val _states = MutableStateFlow<NFCTags.State>(NFCTags.State.Stopped)
    override val states = _states.asStateFlow()

    private val _events = MutableSharedFlow<NFCTags.Event>()
    override val events = _events.asSharedFlow()

    private val mutex = Mutex()

    private fun receivers(context: Context): Flow<Boolean> {
        return callbackFlow {
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val state = intent?.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1)
                    println("[RealNFCTags]:NfcAdapter:state: $state") // todo
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

    private fun start(activity: Activity, lifecycle: Lifecycle) {
        coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                receivers(context = activity).collect { isEnabled ->
                    println("[RealNFCTags]:isEnabled: $isEnabled") // todo
                    if (isEnabled) {
                        if (_states.value == NFCTags.State.Waiting) {
                            _states.value = NFCTags.State.Started
                        }
                    } else {
                        if (_states.value == NFCTags.State.Started) {
                            _states.value = NFCTags.State.Waiting
                        }
                    }
                }
            }
        }
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                callbackFlow<Unit> {
                    println("[RealNFCTags]:on:resume: ${_states.value}") // todo
                    if (_states.value == NFCTags.State.Waiting && adapter.isEnabled) {
                        _states.value = NFCTags.State.Started
                    }
                    awaitClose {
                        println("[RealNFCTags]:on:pause: ${_states.value}") // todo
                        if (_states.value == NFCTags.State.Started) {
                            _states.value = NFCTags.State.Waiting
                        }
                    }
                }.collect()
            }
        }
        val callback = NfcAdapter.ReaderCallback { tag ->
            coroutineScope.launch {
                withContext(default) {
                    _events.emit(NFCTags.Event.OnTag(tag = tag))
                }
            }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        coroutineScope.launch {
            _states.collect { state ->
                println("[RealNFCTags]:state: $state") // todo
                when (state) {
                    NFCTags.State.Started -> {
                        adapter.enableReaderMode(activity, callback, flags, null)
                    }
                    NFCTags.State.Waiting, NFCTags.State.Stopped -> {
                        adapter.disableReaderMode(activity)
                    }
                }
            }
        }
        if (adapter.isEnabled) {
            _states.value = NFCTags.State.Started
        } else {
            _states.value = NFCTags.State.Waiting
        }
    }

    override fun start(activity: ComponentActivity) {
        if (_states.value == NFCTags.State.Stopped) {
            start(activity = activity, lifecycle = activity.lifecycle)
        }
    }

    override fun stop() {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    val state = _states.value
                    if (state != NFCTags.State.Stopped) {
                        _states.value = NFCTags.State.Stopped
                    }
                }
            }
        }
    }
}
