package com.redplayer.app

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Playlist(val id: String, val name: String, val songs: List<Song> = emptyList())

@Serializable
data class LibraryData(
    val favorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val recents: List<Song> = emptyList()
)

/** Biblioteca do usuário salva em JSON no armazenamento interno do app (sem conta). */
class LibraryStore(ctx: Context) {
    private val file = File(ctx.filesDir, "library.json")
    fun load(): LibraryData = try {
        if (file.exists()) Net.json.decodeFromString(LibraryData.serializer(), file.readText()) else LibraryData()
    } catch (e: Exception) { LibraryData() }

    fun save(d: LibraryData) {
        try { file.writeText(Net.json.encodeToString(LibraryData.serializer(), d)) } catch (_: Exception) {}
    }
    fun exportJson(d: LibraryData): String = Net.json.encodeToString(LibraryData.serializer(), d)
    fun importJson(s: String): LibraryData? = try {
        Net.json.decodeFromString(LibraryData.serializer(), s)
    } catch (e: Exception) { null }
}
