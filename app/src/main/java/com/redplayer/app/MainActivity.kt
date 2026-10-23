package com.redplayer.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

val Red = Color(0xFFE5192B)
val Black = Color(0xFF000000)
val Surface1 = Color(0xFF0D0D0D)
val Surface2 = Color(0xFF1A1A1A)
val Grey = Color(0xFFB3B3B3)

private val Scheme = darkColorScheme(
    primary = Red, onPrimary = Color.White, background = Black, onBackground = Color.White,
    surface = Surface1, onSurface = Color.White, surfaceVariant = Surface2,
    onSurfaceVariant = Grey, secondary = Red, surfaceContainerHigh = Surface2,
    surfaceContainer = Surface1, surfaceContainerHighest = Surface2
)

sealed interface Detail {
    data object Favs : Detail
    data object Recents : Detail
    data object Local : Detail
    data class Pl(val id: String) : Detail
}

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
    var detail by remember { mutableStateOf<Detail?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.loadLocal()
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }
    BackHandler(detail != null) { detail = null }

    fun openLocal() {
        detail = Detail.Local
        if (vm.local.isEmpty()) perm.launch(
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    Scaffold(
        containerColor = Black,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Column {
                if (vm.current != null) MiniPlayer(vm) { expanded = true }
                NavigationBar(containerColor = Surface1) {
                    val items = listOf("Início" to Icons.Default.Home, "Buscar" to Icons.Default.Search,
                        "Biblioteca" to Icons.Default.LibraryMusic)
                    items.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i; detail = null },
                            icon = { Icon(icon, label) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White, selectedTextColor = Red,
                                indicatorColor = Red, unselectedIconColor = Grey, unselectedTextColor = Grey))
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val d = detail
            when {
                d != null -> DetailScreen(vm, d, onBack = { detail = null })
                tab == 0 -> HomeScreen(vm)
                tab == 1 -> SearchScreen(vm)
                else -> LibraryScreen(vm, onOpen = { detail = it }, onLocal = ::openLocal)
            }
        }
    }

    if (expanded && vm.current != null) {
        ModalBottomSheet(onDismissRequest = { expanded = false }, containerColor = Black,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { FullPlayer(vm) }
    }
    if (vm.showQueue) {
        ModalBottomSheet(onDismissRequest = { vm.showQueue = false }, containerColor = Black) { QueueSheet(vm) }
    }
    PickPlaylistDialog(vm)
    NewPlaylistDialog(vm)
}

@Composable
fun PickPlaylistDialog(vm: MainViewModel) {
    val song = vm.pickSong ?: return
    AlertDialog(
        onDismissRequest = { vm.pickSong = null },
        containerColor = Surface2,
        title = { Text("Adicionar à playlist") },
        text = {
            Column {
                TextButton({ vm.pendingSong = song; vm.pickSong = null; vm.showNewPlaylist = true }) {
                    Icon(Icons.Default.Add, null, tint = Red); Spacer(Modifier.width(8.dp)); Text("Nova playlist", color = Red)
                }
                vm.lib.playlists.forEach { p ->
                    Text("${p.name}  (${p.songs.size})", Modifier.fillMaxWidth()
                        .clickable { vm.addToPlaylist(p.id, song); vm.pickSong = null }.padding(vertical = 12.dp))
                }
                if (vm.lib.playlists.isEmpty()) Text("Você ainda não tem playlists.", color = Grey)
            }
        },
        confirmButton = { TextButton({ vm.pickSong = null }) { Text("Fechar", color = Red) } }
    )
}

@Composable
fun NewPlaylistDialog(vm: MainViewModel) {
    if (!vm.showNewPlaylist) return
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.showNewPlaylist = false; vm.pendingSong = null },
        containerColor = Surface2,
        title = { Text("Nova playlist") },
        text = {
            OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Nome") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, cursorColor = Red))
        },
        confirmButton = {
            TextButton({
                vm.createPlaylist(name, vm.pendingSong)
                vm.showNewPlaylist = false; vm.pendingSong = null
            }) { Text("Criar", color = Red) }
        },
        dismissButton = {
            TextButton({ vm.showNewPlaylist = false; vm.pendingSong = null }) { Text("Cancelar", color = Grey) }
        }
    )
}

@Composable
fun Cover(url: String?, size: Int) {
    val m = Modifier.size(size.dp).clip(RoundedCornerShape(6.dp)).background(Surface2)
    if (!url.isNullOrBlank()) AsyncImage(url, null, m, contentScale = ContentScale.Crop)
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
            Text(md?.artist?.toString() ?: "", maxLines = 1, color = Grey,
                style = MaterialTheme.typography.bodySmall)
        }
        IconButton({ vm.prev() }) { Icon(Icons.Default.SkipPrevious, "Anterior") }
        IconButton({ vm.toggle() }) {
            Icon(if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Red)
        }
        IconButton({ vm.next() }) { Icon(Icons.Default.SkipNext, "Próxima") }
    }
}
