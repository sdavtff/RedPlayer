package com.redplayer.app

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class Source(val label: String) { DEEZER("Famosas (prévia 30s)"), AUDIUS("Completas (Audius)") }

val Genres = listOf(0 to "Geral", 132 to "Pop", 116 to "Rap/Hip Hop", 152 to "Rock", 113 to "Dance",
    165 to "R&B", 75 to "Brasil", 129 to "Jazz", 98 to "Clássica", 464 to "Metal",
    85 to "Alternativo", 106 to "Eletrônica")

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LibraryStore(app)
    var lib by mutableStateOf(store.load())
        private set

    // catálogo
    var charts by mutableStateOf<List<Song>>(emptyList())
    var genre by mutableIntStateOf(0)
    var trending by mutableStateOf<List<Song>>(emptyList())
    var results by mutableStateOf<List<Song>>(emptyList())
    var source by mutableStateOf(Source.DEEZER)
    var local by mutableStateOf<List<Song>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // Spotify (opcional): token colado pelo usuário, guardado só no aparelho
    private val prefs = app.getSharedPreferences("spotify", 0)
    var spotifyToken by mutableStateOf(prefs.getString("token", "") ?: "")
        private set
    var spotifyTop by mutableStateOf<List<Song>>(emptyList())
    var showSpotify by mutableStateOf(false)

    // UI global
    var pickSong by mutableStateOf<Song?>(null)
    var showNewPlaylist by mutableStateOf(false)
    var pendingSong by mutableStateOf<Song?>(null)
    var showQueue by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)

    // player
    var controller: MediaController? = null
    var current by mutableStateOf<MediaItem?>(null)
    var playing by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var speed by mutableStateOf(1f)
    var queueVersion by mutableIntStateOf(0)
    var lyrics by mutableStateOf<String?>(null)
    var lyricsLoading by mutableStateOf(false)
    var sleepMinutes by mutableIntStateOf(0)
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            current = item; lyrics = null
        }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffle = enabled }
        override fun onRepeatModeChanged(mode: Int) { repeat = mode }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { queueVersion++ }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        f.addListener({
            controller = f.get().also {
                it.addListener(listener); current = it.currentMediaItem; playing = it.isPlaying
                shuffle = it.shuffleModeEnabled; repeat = it.repeatMode
            }
        }, MoreExecutors.directExecutor())
        loadCharts(0); loadTrending()
        if (spotifyToken.isNotBlank()) loadSpotifyTop()
    }

    // ---------- catálogo ----------
    fun loadCharts(g: Int) = viewModelScope.launch {
        genre = g; loading = true; error = null
        try { charts = Deezer.chart(g) } catch (e: Exception) { error = "Sem conexão com o catálogo." }
        loading = false
    }

    fun loadTrending() = viewModelScope.launch {
        try { trending = Audius.trending() } catch (_: Exception) {}
    }

    fun search(q: String) = viewModelScope.launch {
        if (q.isBlank()) return@launch
        loading = true; error = null
        try {
            results = if (source == Source.DEEZER) Deezer.search(q) else Audius.search(q)
            if (results.isEmpty()) error = "Nada encontrado para \"$q\"."
        } catch (e: Exception) { error = "Erro na busca. Verifique a internet." }
        loading = false
    }

    fun loadLocal() { local = LocalMusic.load(getApplication()) }

    fun saveSpotifyToken(t: String) {
        spotifyToken = t.trim().removePrefix("Bearer ").trim()
        prefs.edit().putString("token", spotifyToken).apply()
        if (spotifyToken.isNotEmpty()) loadSpotifyTop() else spotifyTop = emptyList()
    }

    fun loadSpotifyTop() = viewModelScope.launch {
        try {
            val r = Net.api.spotifyTop("https://api.spotify.com/v1/me/top/tracks", "Bearer $spotifyToken")
            spotifyTop = r.items.mapNotNull { t ->
                try { Deezer.match("${t.artists.firstOrNull()?.name ?: ""} ${t.name}") } catch (e: Exception) { null }
            }
            toast = "Spotify: ${spotifyTop.size} faixas do seu top carregadas"
        } catch (e: Exception) {
            toast = "Token do Spotify inválido ou expirado"
        }
    }

    // ---------- reprodução ----------
    fun play(list: List<Song>, index: Int, shuffleOn: Boolean = false, refresh: Boolean = false) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            val c = controller ?: return@launch
            val songs = if (refresh && list.any { it.id.startsWith("dz-") }) Deezer.fresh(list) else list
            c.shuffleModeEnabled = shuffleOn
            val start = if (shuffleOn) songs.indices.random() else index.coerceIn(songs.indices)
            c.setMediaItems(songs.map { it.toItem() }, start, 0L)
            c.prepare(); c.play()
            addRecent(songs[start])
        }
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNext() }
    fun prev() { controller?.seekToPrevious() }
    fun seek(ms: Long) { controller?.seekTo(ms) }
    fun skipTo(i: Int) { controller?.seekToDefaultPosition(i); controller?.play() }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
    fun cycleSpeed() {
        val opts = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        speed = opts[(opts.indexOf(speed) + 1).mod(opts.size)]
        controller?.setPlaybackSpeed(speed)
    }
    fun setSleep(min: Int) {
        sleepJob?.cancel(); sleepMinutes = min
        if (min > 0) sleepJob = viewModelScope.launch {
            delay(min * 60_000L); controller?.pause(); sleepMinutes = 0
            toast = "Timer de sono: música pausada"
        }
    }

    fun queue(): List<MediaItem> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
    }
    fun currentIndex() = controller?.currentMediaItemIndex ?: 0
    fun removeFromQueue(i: Int) { controller?.removeMediaItem(i) }

    fun playNext(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(s), 0) else c.addMediaItem(c.currentMediaItemIndex + 1, s.toItem())
        toast = "Tocará a seguir"
    }
    fun enqueue(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(s), 0) else c.addMediaItem(s.toItem())
        toast = "Adicionada à fila"
    }

    fun loadLyrics() = viewModelScope.launch {
        val s = current?.toSong() ?: return@launch
        lyricsLoading = true
        lyrics = Lyrics.get(s.artist, s.title) ?: "Letra não encontrada."
        lyricsLoading = false
    }

    // ---------- biblioteca ----------
    private fun update(f: (LibraryData) -> LibraryData) { lib = f(lib); store.save(lib) }

    fun isFav(id: String) = lib.favorites.any { it.id == id }
    fun toggleFav(s: Song) = update { l ->
        if (l.favorites.any { it.id == s.id }) l.copy(favorites = l.favorites.filterNot { it.id == s.id })
        else l.copy(favorites = listOf(s) + l.favorites)
    }
    private fun addRecent(s: Song) = update { l ->
        l.copy(recents = (listOf(s) + l.recents.filterNot { it.id == s.id }).take(100))
    }
    fun clearRecents() = update { it.copy(recents = emptyList()) }

    fun createPlaylist(name: String, andAdd: Song? = null) {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifBlank { "Minha playlist" },
            listOfNotNull(andAdd))
        update { it.copy(playlists = it.playlists + p) }
        toast = "Playlist criada"
    }
    fun renamePlaylist(id: String, name: String) = update { l ->
        l.copy(playlists = l.playlists.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })
    }
    fun deletePlaylist(id: String) = update { l -> l.copy(playlists = l.playlists.filterNot { it.id == id }) }
    fun addToPlaylist(pid: String, s: Song) {
        update { l ->
            l.copy(playlists = l.playlists.map {
                if (it.id == pid && it.songs.none { x -> x.id == s.id }) it.copy(songs = it.songs + s) else it
            })
        }
        toast = "Adicionada à playlist"
    }
    fun removeFromPlaylist(pid: String, sid: String) = update { l ->
        l.copy(playlists = l.playlists.map { if (it.id == pid) it.copy(songs = it.songs.filterNot { x -> x.id == sid }) else it })
    }
    fun movePlaylistSong(pid: String, from: Int, to: Int) = update { l ->
        l.copy(playlists = l.playlists.map {
            if (it.id != pid || to !in it.songs.indices) it
            else it.copy(songs = it.songs.toMutableList().also { m -> m.add(to, m.removeAt(from)) })
        })
    }
    fun exportLibrary() = store.exportJson(lib)
    fun importLibrary(json: String): Boolean {
        val d = store.importJson(json) ?: return false
        update { d }; return true
    }

    override fun onCleared() { controller?.run { removeListener(listener); release() } }
}
