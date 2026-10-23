package com.redplayer.app

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var trending by mutableStateOf<List<Song>>(emptyList())
    var results by mutableStateOf<List<Song>>(emptyList())
    var local by mutableStateOf<List<Song>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var controller: MediaController? = null
    var current by mutableStateOf<MediaItem?>(null)
    var playing by mutableStateOf(false)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { current = item }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        f.addListener({
            controller = f.get().also {
                it.addListener(listener); current = it.currentMediaItem; playing = it.isPlaying
            }
        }, MoreExecutors.directExecutor())
        loadTrending()
    }

    fun loadTrending() = viewModelScope.launch {
        loading = true; error = null
        try { trending = Audius.trending() } catch (e: Exception) { error = "Sem conexão: ${e.message}" }
        loading = false
    }

    fun search(q: String) = viewModelScope.launch {
        if (q.isBlank()) return@launch
        loading = true; error = null
        try { results = Audius.search(q) } catch (e: Exception) { error = "Erro na busca: ${e.message}" }
        loading = false
    }

    fun loadLocal() { local = LocalMusic.load(getApplication()) }

    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(list.map { it.toItem() }, index, 0L)
        c.prepare(); c.play()
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNext() }
    fun prev() { controller?.seekToPrevious() }
    fun seek(ms: Long) { controller?.seekTo(ms) }

    override fun onCleared() { controller?.run { removeListener(listener); release() } }
}
