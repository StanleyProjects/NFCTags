package sp.ax.nfctags

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
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

    private fun start(activity: Activity, lifecycle: Lifecycle) {
        val job = SupervisorJob()
        val coroutineScope = CoroutineScope(default + job)
        coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                NFCTagsReceivers.adapter(context = activity).collect { isEnabled ->
                    println("[RealNFCTags]:isEnabled: $isEnabled") // todo
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
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                callbackFlow<Unit> {
                    println("[RealNFCTags]:on:resume: ${_states.value}") // todo
                    if (_states.value == InternalState.Waiting && adapter.isEnabled) {
                        _states.value = InternalState.Searching(tt = null)
                    }
                    awaitClose {
                        println("[RealNFCTags]:on:pause: ${_states.value}") // todo
                        if (_states.value != null) {
                            _states.value = InternalState.Waiting
                        }
                    }
                }.collect()
            }
        }
        val callback = NfcAdapter.ReaderCallback { tag ->
            coroutineScope.launch {
                val state = _states.value
                if (state is InternalState.Searching && state.tt == null) {
                    _events.emit(NFCTags.Event.OnTag(tag = tag))
                }
            }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        coroutineScope.launch {
            var state: InternalState? = null
            _states.collect { newState ->
                val oldState = state
                state = newState
                println("[RealNFCTags]:state: $oldState -> $newState") // todo
                if (oldState is InternalState.Searching) {
                    if (newState !is InternalState.Searching) {
                        adapter.disableReaderMode(activity)
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
                if (oldState is InternalState.Searching && oldState.tt != null) {
                    if (newState !is InternalState.Searching || newState.tt == null || !newState.tt.tag.id.contentEquals(oldState.tt.tag.id)) {
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
            _states.value = InternalState.Searching(tt = null)
        } else {
            _states.value = InternalState.Waiting
        }
    }

    override fun start(activity: ComponentActivity) {
        if (_states.value == null) {
            start(activity = activity, lifecycle = activity.lifecycle)
        }
    }

    override fun follow(tag: Tag) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    val state = _states.value
                    if (state is InternalState.Searching && state.tt == null) {
                        runCatching {
                            IsoDep.get(tag)
                        }.onSuccess { tt ->
                            _states.value = InternalState.Searching(tt = tt)
                        }
                    }
                }
            }
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
