package sp.sample.nfctags

import android.nfc.Tag
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import sp.ax.nfctags.NFCTags
import sp.kx.bytes.toHEX

@Composable
internal fun MainScreen() {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tags = App.tags
    val state: NFCTags.State = tags.states.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED).value
    val tagState = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            tags.events.collect { event ->
                when (event) {
                    is NFCTags.Event.OnTag -> {
                        println("[MainScreen]:nfc:event:tag: ${event.tag.id.toHEX()}") // todo
                        tagState.value = event.tag.id.toHEX()
                        tags.connect(tag = event.tag)
                    }
                }
            }
        }
    }
    val activity = LocalActivity.current as? ComponentActivity ?: TODO()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
        ) {
            BasicText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .wrapContentSize(),
                text = state.name,
            )
            BasicText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        when (state) {
                            NFCTags.State.Stopped -> tags.start(activity = activity)
                            else -> tags.stop()
                        }
                    }
                    .wrapContentSize(),
                text = when (state) {
                    NFCTags.State.Stopped -> "start"
                    else -> "stop"
                },
            )
            BasicText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .wrapContentSize(),
                text = if (state == NFCTags.State.Connected) tagState.value.orEmpty() else "",
            )
        }
    }
}
