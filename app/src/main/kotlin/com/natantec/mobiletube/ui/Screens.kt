package com.natantec.mobiletube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.natantec.mobiletube.AppVm
import com.natantec.mobiletube.FeedState
import com.natantec.mobiletube.data.VideoItem
import com.natantec.mobiletube.data.formatDuration
import com.natantec.mobiletube.data.formatViews
import com.natantec.mobiletube.player.PlayerHolder

private val Red = Color(0xFFFF3D3D)

private val Scheme: ColorScheme = darkColorScheme(
    primary = Red,
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFF272727),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MobileTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

// ======================================================================
// Raiz
// ======================================================================

@Composable
fun AppRoot(vm: AppVm) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Column {
                        if (vm.playing != null && !vm.playerOpen) MiniBar(vm)
                        NavigationBar(containerColor = Color(0xFF0F0F0F)) {
                            val tabs = listOf("🏠" to "Início", "🔍" to "Buscar", "🕘" to "Histórico", "⚙" to "Ajustes")
                            tabs.forEachIndexed { i, (icon, label) ->
                                NavigationBarItem(
                                    selected = vm.tab == i,
                                    onClick = { vm.tab = i },
                                    icon = { Text(icon, fontSize = 20.sp) },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).statusBarsPadding()) {
                    when (vm.tab) {
                        0 -> FeedScreen(vm, vm.home, forSearch = false, header = {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Mobiletube", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { vm.loadHome() }) { Text("Atualizar") }
                            }
                        })
                        1 -> SearchScreen(vm)
                        2 -> HistoryScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
            }
            if (vm.playerOpen && vm.playing != null) PlayerScreen(vm)
        }
    }
}

// ======================================================================
// Listas
// ======================================================================

@Composable
private fun FeedScreen(
    vm: AppVm,
    state: FeedState,
    forSearch: Boolean,
    header: @Composable () -> Unit
) {
    val list = vm.visible(state.items)
    val listState = rememberLazyListState()
    val nearEnd by remember(list.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            list.isNotEmpty() && last >= list.size - 3
        }
    }
    LaunchedEffect(nearEnd, state.canLoadMore, state.loading) {
        if (nearEnd && state.canLoadMore && !state.loading) vm.loadMore(forSearch)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item { header() }
        if (state.error != null && list.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = Color(0xFFFF8A80))
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { if (forSearch) vm.doSearch() else vm.loadHome() }) {
                        Text("Tentar de novo")
                    }
                }
            }
        }
        items(list, key = { it.id }) { v ->
            VideoCard(vm, v)
        }
        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(vm: AppVm) {
    FeedScreen(vm, vm.search, forSearch = true, header = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Buscar no YouTube") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.doSearch() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.doSearch() }) { Text("Ir") }
        }
    })
}

@Composable
private fun HistoryScreen(vm: AppVm) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Histórico", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pausar histórico", Modifier.weight(1f))
                    Switch(checked = vm.historyPaused, onCheckedChange = { vm.setHistoryPaused(it) })
                }
                Text(
                    "Com o histórico pausado, nada novo é salvo. Em qualquer vídeo, " +
                        "toque nos três pontinhos para marcar ou desmarcar como assistido.",
                    fontSize = 12.sp, color = Color.Gray
                )
                if (vm.history.isNotEmpty()) {
                    TextButton(onClick = { vm.clearHistory() }) { Text("Limpar tudo") }
                }
            }
        }
        if (vm.history.isEmpty()) {
            item { Text("Nenhum vídeo no histórico.", Modifier.padding(16.dp), color = Color.Gray) }
        }
        items(vm.history, key = { "h" + it.id }) { v -> VideoCard(vm, v) }
    }
}

@Composable
private fun SettingsScreen(vm: AppVm) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        SettingRow("Bloquear Shorts", "Esconde vídeos curtos das listas", vm.blockShorts) { vm.setBlockShorts(it) }
        SettingRow("SponsorBlock", "Pula patrocínios e pedidos de interação", vm.sponsorBlock) { vm.setSponsorBlock(it) }
        SettingRow("Pausar histórico", "Não salvar o que você assistir", vm.historyPaused) { vm.setHistoryPaused(it) }
        Spacer(Modifier.height(12.dp))
        Text("Qualidade máxima do vídeo")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(360, 480, 720, 1080).forEach { h ->
                FilterChip(
                    selected = vm.maxHeight == h,
                    onClick = { vm.setMaxHeight(h) },
                    label = { Text("${h}p") }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Mobiletube 0.1.0 • sem anúncios", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun SettingRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(sub, fontSize = 12.sp, color = Color.Gray)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

// ======================================================================
// Cartão de vídeo
// ======================================================================

@Composable
private fun VideoCard(vm: AppVm, v: VideoItem) {
    val watched = vm.isWatched(v.id)
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().clickable { vm.open(v) }.padding(bottom = 14.dp)) {
        Box {
            AsyncImage(
                model = v.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF272727))
            )
            val dur = formatDuration(v.durationSec)
            if (dur.isNotEmpty()) {
                Text(
                    dur,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (watched) {
                Text(
                    "✔ Assistido",
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                val meta = listOfNotNull(
                    v.channel.ifEmpty { null },
                    formatViews(v.views).ifEmpty { null },
                    v.uploaded
                ).joinToString(" • ")
                Text(meta, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 20.sp) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (watched) "Desmarcar como assistido" else "Marcar como assistido") },
                        onClick = { vm.toggleWatched(v); menu = false }
                    )
                }
            }
        }
    }
}

// ======================================================================
// Player
// ======================================================================

@Composable
private fun PlayerScreen(vm: AppVm) {
    val v = vm.playing ?: return
    val ctx = LocalContext.current
    BackHandler { vm.closePlayerScreen() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            player = PlayerHolder.get(c)
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (vm.loadingVideo) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            DisposableEffect(Unit) {
                onDispose { /* o player continua tocando em segundo plano */ }
            }
            LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                item {
                    Text(v.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(v.channel, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    vm.playError?.let {
                        Text(it, color = Color(0xFFFF8A80))
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val watched = vm.isWatched(v.id)
                        OutlinedButton(onClick = { vm.toggleWatched(v) }) {
                            Text(if (watched) "✔ Assistido" else "Marcar assistido")
                        }
                        OutlinedButton(onClick = { vm.closePlayerScreen() }) { Text("Minimizar") }
                        OutlinedButton(onClick = { vm.stopPlayback() }) { Text("Parar") }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Text(
                        "Dica: pode sair do app ou apagar a tela — o áudio continua tocando.",
                        fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniBar(vm: AppVm) {
    val v = vm.playing ?: return
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.get(ctx) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF272727)).clickable { vm.playerOpen = true }
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(v.channel, maxLines = 1, fontSize = 11.sp, color = Color.Gray)
        }
        TextButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
            Text(if (isPlaying) "⏸" else "▶", fontSize = 20.sp)
        }
        TextButton(onClick = { vm.stopPlayback() }) { Text("✕", fontSize = 18.sp) }
    }
}
