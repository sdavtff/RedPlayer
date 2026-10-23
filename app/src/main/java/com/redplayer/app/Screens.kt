package com.redplayer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

@Composable
fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))

@Composable
fun SongRow(s: Song, vm: MainViewModel, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    val active = vm.current?.mediaId == s.id
    var menu by remember { mutableStateOf(false) }
    val fav = vm.isFav(s.id)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 6.dp, 4.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(s.artUri, 52)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) Red else Color.White)
            Text(s.artist + if (s.preview) "  •  prévia 30s" else "", maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = Grey, style = MaterialTheme.typography.bodySmall)
        }
        if (fav) Icon(Icons.Default.Favorite, null, tint = Red, modifier = Modifier.size(18.dp))
        Box {
            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Mais") }
            DropdownMenu(menu, { menu = false }, containerColor = Surface2) {
                DropdownMenuItem({ Text("Tocar a seguir") }, { vm.playNext(s); menu = false })
                DropdownMenuItem({ Text("Adicionar à fila") }, { vm.enqueue(s); menu = false })
                DropdownMenuItem({ Text(if (fav) "Remover dos favoritos" else "Favoritar") },
                    { vm.toggleFav(s); menu = false })
                DropdownMenuItem({ Text("Adicionar à playlist") }, { vm.pickSong = s; menu = false })
                if (onRemove != null) DropdownMenuItem({ Text("Remover desta lista", color = Red) },
                    { onRemove(); menu = false })
            }
        }
    }
}

@Composable
fun Status(vm: MainViewModel) {
    if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = Red)
    vm.error?.let { Text(it, color = Red, modifier = Modifier.padding(16.dp)) }
}

// ---------------- Início ----------------
@Composable
fun HomeScreen(vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Red Player", style = MaterialTheme.typography.headlineLarge, color = Red,
                modifier = Modifier.padding(16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Genres) { (id, name) ->
                    FilterChip(vm.genre == id, { vm.loadCharts(id) }, { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Red, selectedLabelColor = Color.White,
                            containerColor = Surface2, labelColor = Grey))
                }
            }
            Status(vm)
        }
        if (vm.charts.isNotEmpty()) item {
            Row(Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("Top músicas (prévias 30s)") }
                FilledTonalButton({ vm.play(vm.charts, 0) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Red, contentColor = Color.White)) {
                    Icon(Icons.Default.PlayArrow, null); Text("Tocar")
                }
            }
        }
        itemsIndexed(vm.charts, key = { _, s -> "c" + s.id }) { i, s -> SongRow(s, vm, { vm.play(vm.charts, i) }) }
        if (vm.trending.isNotEmpty()) item { SectionTitle("Músicas completas (Audius)") }
        itemsIndexed(vm.trending, key = { _, s -> "t" + s.id }) { i, s -> SongRow(s, vm, { vm.play(vm.trending, i) }) }
    }
}

// ---------------- Buscar ----------------
@Composable
fun SearchScreen(vm: MainViewModel) {
    var q by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Artistas, faixas, álbuns…") },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 4.dp),
            keyboardActions = KeyboardActions(onSearch = { vm.search(q) }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, cursorColor = Red),
            trailingIcon = { IconButton({ vm.search(q) }) { Icon(Icons.Default.Search, "Buscar", tint = Red) } })
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Source.entries.forEach { s ->
                FilterChip(vm.source == s, { vm.source = s; if (q.isNotBlank()) vm.search(q) }, { Text(s.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Red, selectedLabelColor = Color.White,
                        containerColor = Surface2, labelColor = Grey))
            }
        }
        Status(vm)
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(vm.results, key = { _, s -> s.id }) { i, s -> SongRow(s, vm, { vm.play(vm.results, i) }) }
        }
    }
}

// ---------------- Biblioteca ----------------
@Composable
fun LibraryCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Red)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title); Text(sub, color = Grey, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LibraryScreen(vm: MainViewModel, onOpen: (Detail) -> Unit, onLocal: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Sua biblioteca", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
            LibraryCard(Icons.Default.Favorite, "Favoritas", "${vm.lib.favorites.size} músicas") { onOpen(Detail.Favs) }
            LibraryCard(Icons.Default.History, "Tocadas recentemente", "${vm.lib.recents.size} músicas") { onOpen(Detail.Recents) }
            LibraryCard(Icons.Default.PhoneAndroid, "No aparelho", "Arquivos de áudio locais") { onLocal() }
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("Playlists") }
                TextButton({ vm.pendingSong = null; vm.showNewPlaylist = true }) {
                    Icon(Icons.Default.Add, null, tint = Red); Text(" Nova", color = Red)
                }
            }
            if (vm.lib.playlists.isEmpty()) Text("Crie sua primeira playlist.", color = Grey, modifier = Modifier.padding(16.dp))
        }
        items(vm.lib.playlists, key = { it.id }) { p ->
            LibraryCard(Icons.Default.QueueMusic, p.name, "${p.songs.size} músicas") { onOpen(Detail.Pl(p.id)) }
        }
    }
}

// ---------------- Detalhe (playlist / favoritas / recentes / local) ----------------
@Composable
fun DetailScreen(vm: MainViewModel, d: Detail, onBack: () -> Unit) {
    val pl = (d as? Detail.Pl)?.let { x -> vm.lib.playlists.firstOrNull { it.id == x.id } }
    val title = when (d) {
        Detail.Favs -> "Favoritas"; Detail.Recents -> "Tocadas recentemente"
        Detail.Local -> "No aparelho"; is Detail.Pl -> pl?.name ?: "Playlist"
    }
    val songs = when (d) {
        Detail.Favs -> vm.lib.favorites; Detail.Recents -> vm.lib.recents
        Detail.Local -> vm.local; is Detail.Pl -> pl?.songs ?: emptyList()
    }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(pl == null, d is Detail.Pl) { if (d is Detail.Pl && pl == null) onBack() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp, 8.dp, 4.dp, 0.dp)) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (d is Detail.Pl || d == Detail.Recents) Box {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Opções") }
                    DropdownMenu(menu, { menu = false }, containerColor = Surface2) {
                        if (d is Detail.Pl) {
                            DropdownMenuItem({ Text("Renomear") }, { rename = true; menu = false })
                            DropdownMenuItem({ Text("Excluir playlist", color = Red) }, { confirmDelete = true; menu = false })
                        } else DropdownMenuItem({ Text("Limpar histórico") }, { vm.clearRecents(); menu = false })
                    }
                }
            }
            Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ vm.play(songs, 0, refresh = true) }, enabled = songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                    Icon(Icons.Default.PlayArrow, null); Text("Tocar")
                }
                OutlinedButton({ vm.play(songs, 0, shuffleOn = true, refresh = true) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Default.Shuffle, null, tint = Red); Text(" Aleatório", color = Color.White)
                }
            }
            if (songs.isEmpty()) Text(
                if (d == Detail.Local) "Nenhuma música encontrada (ou permissão negada)." else "Nada aqui ainda.",
                color = Grey, modifier = Modifier.padding(16.dp))
        }
        itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
            SongRow(s, vm, { vm.play(songs, i, refresh = true) },
                onRemove = when (d) {
                    is Detail.Pl -> ({ vm.removeFromPlaylist(d.id, s.id) })
                    Detail.Favs -> ({ vm.toggleFav(s) })
                    else -> null
                })
        }
    }

    if (rename && pl != null) {
        var n by remember { mutableStateOf(pl.name) }
        AlertDialog(onDismissRequest = { rename = false }, containerColor = Surface2, title = { Text("Renomear") },
            text = { OutlinedTextField(n, { n = it }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, cursorColor = Red)) },
            confirmButton = { TextButton({ vm.renamePlaylist(pl.id, n); rename = false }) { Text("Salvar", color = Red) } },
            dismissButton = { TextButton({ rename = false }) { Text("Cancelar", color = Grey) } })
    }
    if (confirmDelete && pl != null) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = Surface2,
            title = { Text("Excluir \"${pl.name}\"?") },
            confirmButton = { TextButton({ vm.deletePlaylist(pl.id); confirmDelete = false }) { Text("Excluir", color = Red) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancelar", color = Grey) } })
    }
}
