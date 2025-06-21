package sp.ax.nfctags

import android.nfc.Tag
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface NFCTags {
    enum class State {
        Started,
        Waiting,
        Stopped,
    }

    sealed interface Event {
        class OnTag(val tag: Tag) : Event
    }

    val states: StateFlow<State>
    val events: SharedFlow<Event>

    fun start(activity: ComponentActivity)
    fun stop()
}
