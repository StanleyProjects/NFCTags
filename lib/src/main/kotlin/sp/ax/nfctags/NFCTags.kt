package sp.ax.nfctags

import android.nfc.Tag
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
        class OnTag(val tag: Tag) : Event
        class OnResponse(val result: Result<ByteArray>) : Event
    }

    val states: StateFlow<State>
    val events: SharedFlow<Event>

    fun start(activity: ComponentActivity)
    fun follow(tag: Tag)
    fun transceive(bytes: ByteArray)
    fun stop()
}
