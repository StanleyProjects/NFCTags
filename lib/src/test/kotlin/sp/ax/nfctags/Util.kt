package sp.ax.nfctags

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend fun Job.join(delay: Duration = 1.seconds, preJoin: suspend () -> Unit) {
    delay(delay)
    preJoin()
    join()
}

internal suspend fun Job.cancel(delay: Duration = 1.seconds, preCancel: suspend () -> Unit) {
    delay(delay)
    preCancel()
    cancel()
}
