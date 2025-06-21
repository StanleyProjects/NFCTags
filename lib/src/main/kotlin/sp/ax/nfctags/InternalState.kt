package sp.ax.nfctags

import android.nfc.tech.IsoDep

internal sealed interface InternalState {
    class Searching(val tt: IsoDep?) : InternalState {
        override fun toString(): String {
            return "Searching(tt: ${tt?.tag?.id?.size})"
        }

        override fun equals(other: Any?): Boolean {
            return when (other) {
                is Searching -> when (other.tt) {
                    null -> tt == null
                    else -> when (tt) {
                        null -> false
                        else -> other.tt.tag.id.contentEquals(tt.tag.id)
                    }
                }
                else -> false
            }
        }

        override fun hashCode(): Int {
            return tt?.tag?.id?.contentHashCode() ?: 0
        }
    }
    data object Waiting : InternalState
}
