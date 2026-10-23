package com.redplayer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@Composable
fun FullPlayer(vm: MainViewModel) {
    val md = vm.current?.mediaMetadata
    val song = vm.current?.toSong()
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }

    LaunchedEffect(vm.current) { showLyrics = false }
    LaunchedEffect(vm.current, vm.playing) {
        while (true) {
            if (!dragging) {
                pos = vm.controller?.currentPosition ?: 0L
                dur = (vm.controller?.duration ?: 0L).coerceAtLeast(0L)
            }
            delay(500)
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp, 0.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(md?.artworkUri?.toString(), 260)
        Spacer(Modifier.height(16.dp))
        Text(md?.title?.toString() ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 2,
            textAlign = TextAlign.Center)
        Text(md?.artist?.toString() ?: "", color = Grey)
        if (song?.preview == true) Text("Prévia de 30 segundos", color = Red, style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val fav = song != null && vm.isFav(song.id)
            IconButton({ song?.let(vm::toggleFav) }) {
                Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favoritar", tint = Red)
            }
            IconButton({ vm.pickSong = song }) { Icon(Icons.Default.PlaylistAdd, "Adicionar à playlist") }
            IconButton({ vm.showQueue = true }) { Icon(Icons.Default.QueueMusic, "Fila") }
            IconButton({ showLyrics = !showLyrics; if (showLyrics && vm.lyrics == null) vm.loadLyrics() }) {
                Icon(Icons.Default.Notes, "Letra", tint = if (showLyrics) Red else Color.White)
            }
        }

        Slider(value = pos.toFloat(), valueRange = 0f..dur.coerceAtLeast(1L).toFloat(),
            onValueChange = { dragging = true; pos = it.toLong() },
            onValueChangeFinished = { vm.seek(pos); dragging = false },
            colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), style = MaterialTheme.typography.bodySmall)
            Text(fmt(dur), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton({ vm.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, "Aleatório", tint = if (vm.shuffle) Red else Grey)
            }
            IconButton({ vm.prev() }) { Icon(Icons.Default.SkipPrevious, "Anterior", Modifier.size(40.dp)) }
            FilledIconButton({ vm.toggle() }, Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Red)) {
                Icon(if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", Modifier.size(40.dp))
            }
            IconButton({ vm.next() }) { Icon(Icons.Default.SkipNext, "Próxima", Modifier.size(40.dp)) }
            IconButton({ vm.cycleRepeat() }) {
                Icon(if (vm.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repetir", tint = if (vm.repeat == Player.REPEAT_MODE_OFF) Grey else Red)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AssistChip({ vm.cycleSpeed() }, { Text("${vm.speed}x") }, leadingIcon = { Icon(Icons.Default.Speed, null, tint = Red) })
            Box {
                AssistChip({ sleepMenu = true },
                    { Text(if (vm.sleepMinutes > 0) "Sono: ${vm.sleepMinutes} min" else "Timer de sono") },
                    leadingIcon = { Icon(Icons.Default.Bedtime, null, tint = Red) })
                DropdownMenu(sleepMenu, { sleepMenu = false }, containerColor = Surface2) {
                    listOf(0, 15, 30, 45, 60, 90).forEach { m ->
                        DropdownMenuItem({ Text(if (m == 0) "Desligado" else "$m minutos") },
                            { vm.setSleep(m); sleepMenu = false })
                    }
                }
            }
        }
        if (showLyrics) {
            Spacer(Modifier.height(16.dp))
            if (vm.lyricsLoading) CircularProgressIndicator(color = Red)
            else Text(vm.lyrics ?: "", textAlign = TextAlign.Center, color = Color.White,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun QueueSheet(vm: MainViewModel) {
    val q = remember(vm.queueVersion, vm.current) { vm.queue() }
    val cur = vm.currentIndex()
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        Text("Fila de reprodução (${q.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        LazyColumn {
            itemsIndexed(q) { i, item ->
                Row(Modifier.fillMaxWidth().clickable { vm.skipTo(i) }.padding(16.dp, 4.dp, 4.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Cover(item.mediaMetadata.artworkUri?.toString(), 44)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(item.mediaMetadata.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (i == cur) Red else Color.White)
                        Text(item.mediaMetadata.artist?.toString() ?: "", maxLines = 1, color = Grey,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (i != cur) IconButton({ vm.removeFromQueue(i) }) { Icon(Icons.Default.Close, "Remover", tint = Grey) }
                }
            }
        }
    }
}

fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
