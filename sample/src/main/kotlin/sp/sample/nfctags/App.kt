package sp.sample.nfctags

import android.app.Application
import sp.ax.nfctags.NFCTags

internal class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // todo
    }

    companion object {
        private var _tags: NFCTags? = null
        val tags: NFCTags get() = checkNotNull(_tags) { "No tags!" }
    }
}
