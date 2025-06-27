package sp.ax.nfctags

import androidx.activity.ComponentActivity
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

    fun start(activity: ComponentActivity)
    fun transceive(bytes: ByteArray)
    fun unfollow()
    fun stop()
}
