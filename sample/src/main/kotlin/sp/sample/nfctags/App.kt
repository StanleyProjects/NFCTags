package sp.sample.nfctags

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import sp.ax.nfctags.NFCTags
import sp.ax.nfctags.RealNFCTags

internal class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val job = SupervisorJob()
        _tags = RealNFCTags(
            coroutineScope = CoroutineScope(Dispatchers.Main + job),
            default = Dispatchers.Default,
        )
    }

    companion object {
        private var _tags: NFCTags? = null
        val tags: NFCTags get() = checkNotNull(_tags) { "No tags!" }
    }
}
