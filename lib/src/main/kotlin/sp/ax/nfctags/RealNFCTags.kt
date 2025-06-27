package sp.ax.nfctags

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
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
            is InternalState.Searching -> when (state.tt) {
                null -> NFCTags.State.Searching
                else -> NFCTags.State.Following
            }
            InternalState.Waiting -> NFCTags.State.Waiting
            null -> NFCTags.State.Stopped
        }
    }.stateIn(coroutineScope, SharingStarted.Lazily, initialValue = NFCTags.State.Stopped)

    private val _events = MutableSharedFlow<NFCTags.Event>()
    override val events = _events.asSharedFlow()

    private val mutex = Mutex()

    override fun start(activity: ComponentActivity) {
        if (_states.value != null) return
        val job = SupervisorJob()
        val coroutineScope = CoroutineScope(default + job)
        val lifecycle = activity.lifecycle
        coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                NFCTagsReceivers.adapter(context = activity).collect { isEnabled ->
                    mutex.withLock {
                        if (isEnabled) {
                            if (_states.value == InternalState.Waiting) {
                                _states.value = InternalState.Searching(tt = null)
                            }
                        } else {
                            if (_states.value != null) {
                                _states.value = InternalState.Waiting
                            }
                        }
                    }
                }
            }
        }
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: TODO("No adapter!")
        coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                callbackFlow<Unit> {
                    if (_states.value == InternalState.Waiting && adapter.isEnabled) {
                        _states.value = InternalState.Searching(tt = null)
                    }
                    awaitClose {
                        if (_states.value != null) {
                            _states.value = InternalState.Waiting
                        }
                    }
                }.collect()
            }
        }
        val callback = NfcAdapter.ReaderCallback { tag ->
            coroutineScope.launch {
                mutex.withLock {
                    val state = _states.value
                    if (state is InternalState.Searching && state.tt == null) {
                        runCatching {
                            if (tag.id == null) TODO("RealNFCTags:tag:no id!")
                            IsoDep.get(tag) ?: TODO("RealNFCTags:tag:no tag technology!")
                        }.onSuccess { tt: IsoDep ->
                            _states.value = InternalState.Searching(tt = tt)
                        }
                    }
                }
            }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        coroutineScope.launch {
            var state: InternalState? = null
            _states.collect { newState ->
                val oldState = state
                state = newState
                if (oldState is InternalState.Searching) {
                    if (newState !is InternalState.Searching) {
                        adapter.disableReaderMode(activity)
                    }
                    if (oldState.tt == null) {
                        if (newState is InternalState.Searching && newState.tt != null) {
                            _events.emit(NFCTags.Event.OnFollowing(id = newState.tt.tag.id))
                        }
                    } else {
                        val tt = (newState as? InternalState.Searching)?.tt
                        if (tt == null || !tt.tag.id.contentEquals(oldState.tt.tag.id)) {
                            runCatching {
                                oldState.tt.close()
                            }
                        }
                    }
                } else {
                    if (newState is InternalState.Searching) {
                        if (adapter.isEnabled) {
                            adapter.enableReaderMode(activity, callback, flags, null)
                        } else {
                            _states.value = InternalState.Waiting
                        }
                    }
                }
                if (oldState != null && newState == null) {
                    job.cancel()
                }
            }
        }
        if (activity.lifecycle.currentState >= Lifecycle.State.RESUMED && adapter.isEnabled) {
            _states.value = InternalState.Searching(tt = null)
        } else {
            _states.value = InternalState.Waiting
        }
    }

    override fun transceive(bytes: ByteArray) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    val state = _states.value
                    if (state is InternalState.Searching && state.tt != null) {
                        val result = runCatching {
                            if (!state.tt.isConnected) state.tt.connect()
                            state.tt.transceive(bytes)
                        }
                        _events.emit(NFCTags.Event.OnResponse(result = result))
                        if (result.isFailure) {
                            _states.value = InternalState.Searching(tt = null)
                        }
                    }
                }
            }
        }
    }

    override fun unfollow() {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    val state = _states.value
                    if (state is InternalState.Searching && state.tt != null) {
                        _states.value = InternalState.Searching(tt = null)
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
