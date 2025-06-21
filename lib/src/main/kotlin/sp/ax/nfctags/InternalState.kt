package sp.ax.nfctags

import android.nfc.tech.IsoDep

internal sealed interface InternalState {
    data object Searching : InternalState
    class Connected(val tt: IsoDep) : InternalState {
        override fun toString(): String {
            return "Connected(tt: ${tt.tag.id.size})"
        }

        override fun equals(other: Any?): Boolean {
            return when (other) {
                is Connected -> other.tt.tag.id.contentEquals(tt.tag.id)
                else -> false
            }
        }

        override fun hashCode(): Int {
            return tt.tag.id.contentHashCode()
        }
    }
    data object Waiting : InternalState
}
