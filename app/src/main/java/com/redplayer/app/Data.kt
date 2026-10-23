package com.redplayer.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

data class Song(
    val id: String, val title: String, val artist: String,
    val artUri: String?, val mediaUri: String, val durationMs: Long = 0
) {
    fun toItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id).setUri(mediaUri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist)
                .setArtworkUri(artUri?.let(Uri::parse)).build())
        .build()
}

@Serializable data class HostsResp(val data: List<String>)
@Serializable data class TracksResp(val data: List<ATrack>)
@Serializable data class ATrack(
    val id: String, val title: String, val duration: Int = 0,
    val user: AUser, val artwork: AArt? = null
)
@Serializable data class AUser(val name: String)
@Serializable data class AArt(@SerialName("480x480") val large: String? = null)

interface AudiusApi {
    @GET suspend fun hosts(@Url url: String): HostsResp
    @GET suspend fun tracks(@Url url: String, @Query("app_name") app: String = APP,
                            @Query("query") q: String? = null, @Query("limit") limit: Int = 30): TracksResp
    companion object { const val APP = "RedPlayer" }
}

/** Audius: catálogo de música gratuito, sem chave de API, sem login. */
object Audius {
    private val json = Json { ignoreUnknownKeys = true }
    private val api = Retrofit.Builder().baseUrl("https://api.audius.co/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(AudiusApi::class.java)
    private var host: String? = null

    private suspend fun host(): String =
        host ?: api.hosts("https://api.audius.co").data.first().trimEnd('/').also { host = it }

    private suspend fun map(r: TracksResp): List<Song> {
        val h = host()
        return r.data.map {
            Song(it.id, it.title, it.user.name, it.artwork?.large,
                "$h/v1/tracks/${it.id}/stream?app_name=${AudiusApi.APP}", it.duration * 1000L)
        }
    }
    suspend fun trending() = map(api.tracks("${host()}/v1/tracks/trending"))
    suspend fun search(q: String) = map(api.tracks("${host()}/v1/tracks/search", q = q))
}

object LocalMusic {
    fun load(ctx: Context): List<Song> {
        val out = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        ctx.contentResolver.query(uri, cols, "${MediaStore.Audio.Media.IS_MUSIC}!=0", null,
            "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += Song("local-$id", c.getString(1) ?: "Sem título",
                    c.getString(2) ?: "Desconhecido", null,
                    ContentUris.withAppendedId(uri, id).toString(), c.getLong(3))
            }
        }
        return out
    }
}
