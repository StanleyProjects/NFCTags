package sp.sample.nfctags

import android.nfc.tech.IsoDep
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
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
        val root = FrameLayout(this).also {
            it.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val button = Button(this).also {
            it.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL,
            )
            root.addView(it)
        }
        setContentView(root)
        val tags = RealNFCTags(
            coroutineScope = lifecycle.coroutineScope,
            default = Dispatchers.Default,
            activity = this,
        )
        lifecycle.coroutineScope.launch {
            tags.states.collect { state ->
                println("[MainActivity]:nfc:state: $state") // todo
                when (state) {
                    NFCTags.State.Started -> {
                        button.text = "stop"
                        button.setOnClickListener { _ ->
                            tags.stop()
                        }
                    }
                    NFCTags.State.Waiting -> {
                        button.text = "stop"
                        button.setOnClickListener { _ ->
                            tags.stop()
                        }
                    }
                    NFCTags.State.Stopped -> {
                        button.text = "start"
                        button.setOnClickListener { _ ->
                            tags.start()
                        }
                    }
                }
            }
        }
        lifecycle.coroutineScope.launch {
            tags.events.collect { event ->
                when (event) {
                    is NFCTags.Event.OnTag -> {
                        val tt = IsoDep.get(event.tag)
                        println("[MainActivity]:nfc:event:tag: ${tt?.tag?.id?.toHEX()}") // todo
                    }
                }
            }
        }
    }
}
