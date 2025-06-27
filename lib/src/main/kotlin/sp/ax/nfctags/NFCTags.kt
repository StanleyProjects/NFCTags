package sp.ax.nfctags

import android.app.Activity
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface NFCTags {
    enum class State {
        Searching,
        Waiting,
        Stopped,
        Following,
    }

    sealed interface Event {
        class OnFollowing(val id: ByteArray) : Event
        class OnResponse(val result: Result<ByteArray>) : Event
    }

    val states: StateFlow<State>
    val events: SharedFlow<Event>

    fun start(activity: Activity, lifecycle: Lifecycle)
    fun transceive(bytes: ByteArray)
    fun unfollow()
    fun stop()
}
