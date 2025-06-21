package sp.sample.nfctags

import android.nfc.tech.IsoDep
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.launch
import sp.ax.nfctags.NFCTags
import sp.kx.bytes.toHEX

internal class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        val view = ComposeView(this)
//        setContentView(view)
//        view.setContent {
//            MainScreen()
//        }
        val root = LinearLayout(this).also {
            it.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            it.orientation = LinearLayout.VERTICAL
            it.gravity = Gravity.CENTER_VERTICAL
        }
        val button = Button(this).also {
            it.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            root.addView(it)
        }
        val tv = TextView(this).also {
            it.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            root.addView(it)
        }
        setContentView(root)
        val tags = App.tags
        val activity: ComponentActivity = this
        lifecycle.coroutineScope.launch {
            tags.states.collect { state ->
                println("[MainActivity]:nfc:state: $state") // todo
                when (state) {
                    NFCTags.State.Connected -> {
                        tv.visibility = View.VISIBLE
                    }
                    else -> {
                        tv.visibility = View.INVISIBLE
                    }
                }
                when (state) {
                    NFCTags.State.Searching, NFCTags.State.Waiting, NFCTags.State.Connected -> {
                        button.text = "stop"
                        button.setOnClickListener { _ ->
                            tags.stop()
                        }
                    }
                    NFCTags.State.Stopped -> {
                        button.text = "start"
                        button.setOnClickListener { _ ->
                            tags.start(activity = activity)
                        }
                    }
                }
            }
        }
        lifecycle.coroutineScope.launch {
            tags.events.collect { event ->
                when (event) {
                    is NFCTags.Event.OnTag -> {
                        println("[MainActivity]:nfc:event:tag: ${event.tag.id.toHEX()}") // todo
                        tags.connect(tag = event.tag)
                        tv.text = event.tag.id.toHEX()
                    }
                }
            }
        }
    }
}
