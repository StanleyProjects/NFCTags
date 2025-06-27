package sp.sample.nfctags

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
    val state = tags.states.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED).value
    val tagState = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        when (state) {
            NFCTags.State.Following -> {
                val ints = intArrayOf(0x30, 0x00)
                val bytes = ints.map { it.toByte() }.toByteArray()
                println("[MainScreen]:transceive: ${bytes.toHEX()}") // todo
                tags.transceive(bytes = bytes)
            }
            else -> {
                tagState.value = null
            }
        }
    }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            tags.events.collect { event ->
                when (event) {
                    is NFCTags.Event.OnFollowing -> {
                        println("[MainScreen]:nfc:event:tag: ${event.id.toHEX()}") // todo
                        tagState.value = event.id.toHEX()
                    }
                    is NFCTags.Event.OnResponse -> {
                        event.result.fold(
                            onSuccess = { bytes ->
                                println("[MainScreen]:nfc:event:response: ${bytes.toHEX()}") // todo
                                tags.unfollow()
                            },
                            onFailure = { error ->
                                println("[MainScreen]:response:error(${error::class.java.name}): $error") // todo
                                error.printStackTrace() // todo
                            },
                        )
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
                            NFCTags.State.Stopped -> tags.start(activity = activity, lifecycle = activity.lifecycle)
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
                text = tagState.value.orEmpty(),
            )
        }
    }
}
