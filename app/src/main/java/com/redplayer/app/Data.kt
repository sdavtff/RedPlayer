package com.redplayer.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val artUri: String? = null,
    val mediaUri: String = "",
    val durationMs: Long = 0,
    val preview: Boolean = false,
    val album: String = ""
) {
    fun toItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id).setUri(mediaUri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
                .setArtworkUri(artUri?.let(Uri::parse)).build())
        .build()
}

fun MediaItem.toSong() = Song(
    mediaId, mediaMetadata.title?.toString() ?: "", mediaMetadata.artist?.toString() ?: "",
    mediaMetadata.artworkUri?.toString(), localConfiguration?.uri?.toString() ?: "",
    0L, mediaId.startsWith("dz-"), mediaMetadata.albumTitle?.toString() ?: ""
)

// ---------- Audius (músicas completas, gratuitas, sem chave) ----------
@Serializable data class HostsResp(val data: List<String>)
@Serializable data class TracksResp(val data: List<ATrack>)
@Serializable data class ATrack(
    val id: String, val title: String, val duration: Int = 0,
    val user: AUser, val artwork: AArt? = null, val genre: String? = null
)
@Serializable data class AUser(val name: String)
@Serializable data class AArt(@SerialName("480x480") val large: String? = null)

// ---------- Deezer (prévias oficiais de 30s, API pública, sem chave) ----------
@Serializable data class DzResp(val data: List<DzTrack> = emptyList())
@Serializable data class DzTrack(
    val id: Long, val title: String, val duration: Int = 0, val preview: String = "",
    val artist: DzArtist, val album: DzAlbum? = null
)
@Serializable data class DzArtist(val name: String)
@Serializable data class DzAlbum(val title: String = "", @SerialName("cover_big") val cover: String? = null)

// ---------- Spotify Web API (opcional, token do usuário; só metadados) ----------
@Serializable data class SpTop(val items: List<SpTrack> = emptyList())
@Serializable data class SpTrack(val name: String, val artists: List<AUser> = emptyList())

// ---------- MusicAPI (api.musicapi.com): busca unificada em vários serviços ----------
@Serializable data class MaReq(val track: String, val artist: String? = null,
                               val type: String = "track", val sources: List<String>)
@Serializable data class MaIntro(val sources: List<String> = emptyList())
@Serializable data class MaResp(val tracks: List<MaItem> = emptyList())
@Serializable data class MaItem(val source: String = "", val status: String = "", val data: MaData? = null)
@Serializable data class MaData(
    val externalId: String = "", val name: String = "", val artistNames: List<String>? = null,
    val albumName: String? = null, val imageUrl: String? = null, val previewUrl: String? = null
)

// ---------- LRCLIB (letras, gratuito) ----------
@Serializable data class LyricsResp(val plainLyrics: String? = null)

interface Api {
    @GET suspend fun hosts(@Url url: String): HostsResp
    @GET suspend fun audius(@Url url: String, @Query("app_name") app: String = APP,
                            @Query("query") q: String? = null, @Query("limit") limit: Int = 40): TracksResp
    @GET suspend fun deezer(@Url url: String, @Query("q") q: String? = null,
                            @Query("limit") limit: Int = 50): DzResp
    @GET suspend fun spotifyTop(@Url url: String, @Header("Authorization") auth: String,
                                @Query("time_range") range: String = "long_term",
                                @Query("limit") limit: Int = 20): SpTop
    @GET suspend fun maIntro(@Url url: String, @Header("Authorization") auth: String): MaIntro
    @POST suspend fun maSearch(@Url url: String, @Header("Authorization") auth: String,
                               @Body body: MaReq): MaResp
    @GET suspend fun deezerTrack(@Url url: String): DzTrack
    @GET suspend fun lyrics(@Url url: String, @Query("artist_name") artist: String,
                            @Query("track_name") title: String): LyricsResp
    companion object { const val APP = "RedPlayer" }
}

object Net {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    val api: Api = Retrofit.Builder().baseUrl("https://api.audius.co/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(Api::class.java)
}

object Audius {
    private var host: String? = null
    private suspend fun host(): String =
        host ?: Net.api.hosts("https://api.audius.co").data.first().trimEnd('/').also { host = it }

    private suspend fun map(r: TracksResp): List<Song> {
        val h = host()
        return r.data.map {
            Song("au-${it.id}", it.title, it.user.name, it.artwork?.large,
                "$h/v1/tracks/${it.id}/stream?app_name=${Api.APP}", it.duration * 1000L)
        }
    }
    suspend fun trending() = map(Net.api.audius("${host()}/v1/tracks/trending"))
    suspend fun search(q: String) = map(Net.api.audius("${host()}/v1/tracks/search", q = q))
}

object Deezer {
    private const val B = "https://api.deezer.com"
    private fun map(r: DzResp) = r.data.filter { it.preview.isNotBlank() }.map { toSong(it) }
    private fun toSong(t: DzTrack) = Song("dz-${t.id}", t.title, t.artist.name, t.album?.cover,
        t.preview, 30_000L, true, t.album?.title ?: "")

    suspend fun chart(genre: Int) = map(Net.api.deezer("$B/chart/$genre/tracks", limit = 50))
    suspend fun search(q: String) = map(Net.api.deezer("$B/search", q = q, limit = 50))
    suspend fun match(q: String): Song? =
        Net.api.deezer("$B/search", q = q, limit = 3).data.firstOrNull { it.preview.isNotBlank() }?.let { toSong(it) }

    /** Links de prévia expiram; renova antes de tocar músicas salvas. */
    suspend fun fresh(songs: List<Song>): List<Song> = coroutineScope {
        songs.map { s ->
            async {
                if (!s.id.startsWith("dz-")) s
                else try { toSong(Net.api.deezerTrack("$B/track/${s.id.removePrefix("dz-")}")) }
                catch (e: Exception) { s }
            }
        }.awaitAll()
    }
}

object MusicApi {
    private const val B = "https://api.musicapi.com"
    private var sources: List<String>? = null

    /** Usa só o Client ID (público) com o header "Token", válido nas rotas /public. */
    suspend fun search(clientId: String, q: String): List<Song> {
        val auth = "Token $clientId"
        val src = sources ?: Net.api.maIntro("$B/public/search/introspection", auth).sources.also { sources = it }
        val r = Net.api.maSearch("$B/public/search", auth, MaReq(track = q, sources = src))
        return r.tracks.mapNotNull { t ->
            val d = t.data ?: return@mapNotNull null
            val p = d.previewUrl
            if (p.isNullOrBlank()) null
            else Song("ma-${t.source}-${d.externalId}", d.name, d.artistNames?.joinToString(", ") ?: "",
                d.imageUrl, p, 30_000L, true, d.albumName ?: "")
        }.distinctBy { it.id }
    }
}

object Lyrics {
    suspend fun get(artist: String, title: String): String? = try {
        Net.api.lyrics("https://lrclib.net/api/get", artist, title).plainLyrics
    } catch (e: Exception) { null }
}

object LocalMusic {
    fun load(ctx: Context): List<Song> {
        val out = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM)
        ctx.contentResolver.query(uri, cols, "${MediaStore.Audio.Media.IS_MUSIC}!=0", null,
            "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += Song("local-$id", c.getString(1) ?: "Sem título",
                    c.getString(2) ?: "Desconhecido", null,
                    ContentUris.withAppendedId(uri, id).toString(), c.getLong(3), false, c.getString(4) ?: "")
            }
        }
        return out
    }
}
