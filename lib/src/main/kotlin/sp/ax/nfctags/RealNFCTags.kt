package sp.ax.nfctags

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class RealNFCTags(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
) : NFCTags {
    private val _states = MutableStateFlow<InternalState?>(null)
    override val states = _states.map { state ->
        when (state) {
            InternalState.Searching -> NFCTags.State.Searching
            is InternalState.Connected -> NFCTags.State.Connected
            InternalState.Waiting -> NFCTags.State.Waiting
            null -> NFCTags.State.Stopped
        }
    }.stateIn(coroutineScope, SharingStarted.Lazily, initialValue = NFCTags.State.Stopped)

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
        val job = SupervisorJob()
        val coroutineScope = CoroutineScope(default + job)
        coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                receivers(context = activity).collect { isEnabled ->
                    println("[RealNFCTags]:isEnabled: $isEnabled") // todo
                    mutex.withLock {
                        if (isEnabled) {
                            if (_states.value == InternalState.Waiting) {
                                _states.value = InternalState.Searching
                            }
                        } else {
                            when (_states.value) {
                                is InternalState.Connected, InternalState.Searching -> {
                                    _states.value = InternalState.Waiting
                                }
                                else -> {
                                    // noop
                                }
                            }
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
                    if (_states.value == InternalState.Waiting && adapter.isEnabled) {
                        _states.value = InternalState.Searching
                    }
                    awaitClose {
                        println("[RealNFCTags]:on:pause: ${_states.value}") // todo
                        when (_states.value) {
                            is InternalState.Connected, InternalState.Searching -> {
                                _states.value = InternalState.Waiting
                            }
                            else -> {
                                // noop
                            }
                        }
                    }
                }.collect()
            }
        }
        val callback = NfcAdapter.ReaderCallback { tag ->
            coroutineScope.launch {
                _events.emit(NFCTags.Event.OnTag(tag = tag))
            }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        coroutineScope.launch {
            var state: InternalState? = null
            _states.collect { newState ->
                val oldState = state
                state = newState
                println("[RealNFCTags]:state: $oldState -> $newState") // todo
                if (oldState == InternalState.Searching) {
                    if (newState != InternalState.Searching) {
                        adapter.disableReaderMode(activity)
                    }
                } else {
                    if (newState == InternalState.Searching) {
                        adapter.enableReaderMode(activity, callback, flags, null)
                    }
                }
                if (oldState is InternalState.Connected) {
                    if (newState !is InternalState.Connected) {
                        runCatching {
                            oldState.tt.close()
                        }
                    }
                }
                if (oldState != null && newState == null) {
                    job.cancel()
                }
            }
        }
        if (adapter.isEnabled) {
            _states.value = InternalState.Searching
        } else {
            _states.value = InternalState.Waiting
        }
    }

    override fun start(activity: ComponentActivity) {
        if (_states.value == null) {
            start(activity = activity, lifecycle = activity.lifecycle)
        }
    }

    override fun connect(tag: Tag) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    if (_states.value == InternalState.Searching) {
                        runCatching {
                            val tt = IsoDep.get(tag)
                            tt.connect()
                            tt
                        }.onSuccess { tt ->
                            _states.value = InternalState.Connected(tt = tt)
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    if (_states.value != null) _states.value = null
                }
            }
        }
    }
}
