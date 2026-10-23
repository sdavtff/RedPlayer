package com.redplayer.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

val Red = Color(0xFFE5192B)
val Black = Color(0xFF000000)
val Surface1 = Color(0xFF0D0D0D)
val Surface2 = Color(0xFF1A1A1A)

private val Scheme = darkColorScheme(
    primary = Red, onPrimary = Color.White, background = Black, onBackground = Color.White,
    surface = Surface1, onSurface = Color.White, surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFB3B3B3), secondary = Red
)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = Scheme) { App(vm) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.loadLocal()
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        containerColor = Black,
        bottomBar = {
            Column {
                if (vm.current != null) MiniPlayer(vm) { expanded = true }
                NavigationBar(containerColor = Surface1) {
                    val items = listOf("Descobrir" to Icons.Default.Whatshot,
                        "Buscar" to Icons.Default.Search, "No aparelho" to Icons.Default.PhoneAndroid)
                    items.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(selected = tab == i, onClick = {
                            tab = i
                            if (i == 2 && vm.local.isEmpty()) {
                                perm.launch(if (Build.VERSION.SDK_INT >= 33)
                                    Manifest.permission.READ_MEDIA_AUDIO
                                else Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }, icon = { Icon(icon, label) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White, selectedTextColor = Red,
                                indicatorColor = Red, unselectedIconColor = Color(0xFFB3B3B3),
                                unselectedTextColor = Color(0xFFB3B3B3)))
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> SongList("Em alta", vm.trending, vm, vm.loading, vm.error)
                1 -> SearchTab(vm)
                else -> SongList("Músicas do aparelho", vm.local, vm, false,
                    if (vm.local.isEmpty()) "Nenhuma música encontrada (ou permissão negada)." else null)
            }
        }
    }
    if (expanded && vm.current != null) {
        ModalBottomSheet(onDismissRequest = { expanded = false }, containerColor = Black,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { FullPlayer(vm) }
    }
}

@Composable
fun SearchTab(vm: MainViewModel) {
    var q by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Artistas, faixas…") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.search(q) }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, cursorColor = Red),
            trailingIcon = { IconButton({ vm.search(q) }) { Icon(Icons.Default.Search, "Buscar", tint = Red) } })
        SongList(null, vm.results, vm, vm.loading, vm.error)
    }
}

@Composable
fun SongList(title: String?, songs: List<Song>, vm: MainViewModel, loading: Boolean, error: String?) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (title != null) item {
            Text(title, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp))
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = Red) }
        if (error != null) item { Text(error, color = Red, modifier = Modifier.padding(16.dp)) }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            val active = vm.current?.mediaId == s.id
            Row(Modifier.fillMaxWidth().clickable { vm.play(songs, i) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Cover(s.artUri, 52)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (active) Red else Color.White)
                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun Cover(url: String?, size: Int) {
    val m = Modifier.size(size.dp).clip(RoundedCornerShape(6.dp)).background(Surface2)
    if (url != null) AsyncImage(url, null, m, contentScale = ContentScale.Crop)
    else Box(m, contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Red) }
}

@Composable
fun MiniPlayer(vm: MainViewModel, onOpen: () -> Unit) {
    val md = vm.current?.mediaMetadata
    Row(Modifier.fillMaxWidth().background(Surface2).clickable { onOpen() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(md?.artworkUri?.toString(), 44)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(md?.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(md?.artist?.toString() ?: "", maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        IconButton({ vm.toggle() }) {
            Icon(if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Red)
        }
    }
}

@Composable
fun FullPlayer(vm: MainViewModel) {
    val md = vm.current?.mediaMetadata
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(vm.current, vm.playing) {
        while (true) {
            if (!dragging) {
                pos = vm.controller?.currentPosition ?: 0L
                dur = (vm.controller?.duration ?: 0L).coerceAtLeast(0L)
            }
            delay(500)
        }
    }
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(md?.artworkUri?.toString(), 280)
        Spacer(Modifier.height(24.dp))
        Text(md?.title?.toString() ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 2)
        Text(md?.artist?.toString() ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = pos.toFloat(), valueRange = 0f..(dur.coerceAtLeast(1L)).toFloat(),
            onValueChange = { dragging = true; pos = it.toLong() },
            onValueChangeFinished = { vm.seek(pos); dragging = false },
            colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), style = MaterialTheme.typography.bodySmall)
            Text(fmt(dur), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton({ vm.prev() }) { Icon(Icons.Default.SkipPrevious, "Anterior", Modifier.size(40.dp)) }
            FilledIconButton({ vm.toggle() }, Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Red)) {
                Icon(if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", Modifier.size(40.dp))
            }
            IconButton({ vm.next() }) { Icon(Icons.Default.SkipNext, "Próxima", Modifier.size(40.dp)) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
