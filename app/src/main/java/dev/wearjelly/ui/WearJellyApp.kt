package dev.wearjelly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.LibraryKind
import dev.wearjelly.playback.PlaybackState

private val WearThemeColors = Colors(
    primary = Color(0xFF8CB4FF),
    primaryVariant = Color(0xFF5B8DEF),
    secondary = Color(0xFF64B5F6),
    background = Color(0xFF000000),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color(0xFF001A40),
    onSecondary = Color(0xFF001A40),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFE0E0E0),
    onError = Color(0xFFFFFFFF),
    error = Color(0xFFFF6B6B)
)

@Composable
fun WearJellyApp(viewModel: AppViewModel) {
    MaterialTheme(colors = WearThemeColors) {
        val uiState by viewModel.uiState.collectAsState()
        val dismissBoxState = rememberSwipeToDismissBoxState()

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) {
            SwipeToDismissBox(
                onDismissed = { viewModel.goBack() },
                modifier = Modifier.fillMaxSize(),
                content = { isBackground ->
                    if (isBackground) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    } else {
                        when (val screen = uiState.screen) {
                            is AppScreen.Login -> LoginScreen(viewModel, uiState.login)
                            is AppScreen.Home -> HomeScreen(viewModel, uiState.account)
                            is AppScreen.Library -> LibraryScreen(viewModel, screen)
                            is AppScreen.Track -> TrackDetailScreen(viewModel, screen.item, screen.source)
                            is AppScreen.Player -> PlayerScreen(viewModel)
                            is AppScreen.Queue -> QueueScreen(viewModel)
                            is AppScreen.Lyrics -> LyricsScreen(viewModel)
                            is AppScreen.Settings -> SettingsScreen(viewModel, uiState.account)
                            is AppScreen.Confirm -> ConfirmScreen(viewModel, screen.action)
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun LoginScreen(viewModel: AppViewModel, loginUi: LoginUi) {
    val listState = rememberScalingLazyListState()
    val focusManager = LocalFocusManager.current

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "登录 Jellyfin",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (loginUi.error != null) {
            item {
                Text(
                    text = loginUi.error,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            OutlinedTextField(
                value = loginUi.server,
                onValueChange = { viewModel.changeServer(it) },
                label = { Text("服务器地址", fontSize = 10.sp, color = Color.Gray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        item {
            OutlinedTextField(
                value = loginUi.username,
                onValueChange = { viewModel.changeUsername(it) },
                label = { Text("用户名", fontSize = 10.sp, color = Color.Gray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        item {
            OutlinedTextField(
                value = loginUi.password,
                onValueChange = { viewModel.changePassword(it) },
                label = { Text("密码", fontSize = 10.sp, color = Color.Gray) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    viewModel.login()
                }),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        item {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.login()
                },
                enabled = !loginUi.submitting,
                modifier = Modifier.fillMaxWidth(0.9f).height(40.dp),
                colors = ButtonDefaults.primaryButtonColors()
            ) {
                if (loginUi.submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("登录", style = MaterialTheme.typography.button)
                }
            }
        }
    }
}

@Composable
internal fun HomeScreen(viewModel: AppViewModel, account: AccountUi?) {
    val listState = rememberScalingLazyListState()
    val pbState by viewModel.playbackState.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "WearJelly",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (account != null) {
            item {
                Text(
                    text = "欢迎, ${account.userName}",
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray
                )
            }
        }

        if (pbState.current != null) {
            item {
                Button(
                    onClick = { viewModel.navigate(AppScreen.Player) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (pbState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = pbState.current?.name ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.caption1
                            )
                            Text(
                                text = pbState.current?.artistText ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.caption2,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.openLibrary(LibraryKind.ARTISTS) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("艺人")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.openLibrary(LibraryKind.ALBUMS) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Album, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("专辑")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.openLibrary(LibraryKind.SONGS) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("所有歌曲")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.navigate(AppScreen.Settings) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("设置")
                }
            }
        }
    }
}

@Composable
internal fun LibraryScreen(viewModel: AppViewModel, screen: AppScreen.Library) {
    val listState = rememberScalingLazyListState()
    val uiState by viewModel.uiState.collectAsState()
    val lib = uiState.libraries[screen.query] ?: LibraryUi()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = screen.title,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (screen.query.kind == LibraryKind.ALBUMS && screen.query.artistId != null) {
            item {
                Button(
                    onClick = { viewModel.openArtistSongs(screen) },
                    modifier = Modifier.fillMaxWidth(0.9f).height(38.dp),
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Text("查看该艺人全部歌曲", fontSize = 12.sp)
                }
            }
        }

        if (lib.loading && lib.items.isEmpty()) {
            item {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        } else if (lib.error != null && lib.items.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = lib.error, color = MaterialTheme.colors.error, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.retryLibrary(screen.query) }) {
                        Text("重试")
                    }
                }
            }
        } else {
            itemsIndexed(lib.items) { index, item ->
                LibraryItemRow(item = item, query = screen.query, onClick = {
                    viewModel.openItem(screen.query, item)
                })

                if (index == lib.items.lastIndex && !lib.endReached && !lib.loading) {
                    viewModel.loadMore(screen.query)
                }
            }

            if (lib.loading) {
                item {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp))
                }
            }
        }
    }
}

@Composable
internal fun LibraryItemRow(item: JellyfinItem, query: LibraryQuery, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (query.kind) {
                LibraryKind.ARTISTS -> Icons.Default.Person
                LibraryKind.ALBUMS -> Icons.Default.Album
                LibraryKind.SONGS -> Icons.Default.MusicNote
            }
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.body2
                )
                if (query.kind != LibraryKind.ARTISTS) {
                    Text(
                        text = item.artistText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.caption2,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
internal fun TrackDetailScreen(viewModel: AppViewModel, item: JellyfinItem, source: LibraryQuery) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = item.name,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        item {
            Text(
                text = item.artistText,
                style = MaterialTheme.typography.caption1,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        item {
            Button(
                onClick = { viewModel.playTrack(item) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.primaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("单曲播放")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.playLoaded(source, item.id) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("以此开始播放全部")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.enqueue(item) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("加入待播队列")
            }
        }
    }
}

@Composable
internal fun PlayerScreen(viewModel: AppViewModel) {
    val pbState by viewModel.playbackState.collectAsState()
    val listState = rememberScalingLazyListState()
    val item = pbState.current

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            val artUrl = item?.let { viewModel.artworkUrl(it) }
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                }
            }
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item?.name ?: "未播放",
                    style = MaterialTheme.typography.title3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = item?.artistText ?: "Jellyfin",
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            val currentMs = pbState.positionMs
            val totalMs = pbState.effectiveDurationMs
            val progress = if (totalMs > 0) (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.85f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.DarkGray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(MaterialTheme.colors.primary)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(currentMs), fontSize = 10.sp, color = Color.Gray)
                    Text(text = formatTime(totalMs), fontSize = 10.sp, color = Color.Gray)
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Icon(
                        imageVector = if (pbState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.seekRelative(-15000L) },
                    modifier = Modifier.size(32.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(32.dp),
                    colors = if (pbState.shuffleEnabled) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.cycleRepeatMode() },
                    modifier = Modifier.size(32.dp),
                    colors = if (pbState.repeatMode != Player.REPEAT_MODE_OFF) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors()
                ) {
                    val icon = if (pbState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.seekRelative(15000L) },
                    modifier = Modifier.size(32.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.navigate(AppScreen.Queue) },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text("队列 (${pbState.queue.size})", fontSize = 11.sp)
                }
                Button(
                    onClick = { viewModel.navigate(AppScreen.Lyrics) },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text("歌词", fontSize = 11.sp)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun QueueScreen(viewModel: AppViewModel) {
    val pbState by viewModel.playbackState.collectAsState()
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "播放队列",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        item {
            Button(
                onClick = { viewModel.requestConfirmation(ConfirmAction.CLEAR_QUEUE) },
                modifier = Modifier.fillMaxWidth(0.8f).height(36.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A1515))
            ) {
                Text("清空队列", fontSize = 12.sp, color = Color.White)
            }
        }

        if (pbState.queue.isEmpty()) {
            item {
                Text("队列为空", color = Color.Gray, style = MaterialTheme.typography.caption2)
            }
        } else {
            itemsIndexed(pbState.queue) { index, track ->
                val isCurrent = index == pbState.currentIndex
                Button(
                    onClick = { viewModel.playQueueIndex(index, track.item.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isCurrent) Color(0xFF1E3A66) else MaterialTheme.colors.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 11.sp,
                            color = if (isCurrent) MaterialTheme.colors.primary else Color.Gray,
                            modifier = Modifier.width(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.item.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = track.item.artistText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "移除",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    viewModel.removeQueueItem(index, track.item.id)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LyricsScreen(viewModel: AppViewModel) {
    val listState = rememberScalingLazyListState()
    val uiState by viewModel.uiState.collectAsState()
    val pbState by viewModel.playbackState.collectAsState()
    val lyrics = uiState.lyrics.lyrics

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "歌词",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (uiState.lyrics.loading) {
            item {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        } else if (lyrics == null || lyrics.lines.isEmpty()) {
            item {
                Text("暂无歌词", color = Color.Gray, style = MaterialTheme.typography.body2)
            }
        } else {
            itemsIndexed(lyrics.lines) { index, line ->
                val isHighlighted = if (lyrics.isSynchronized && line.startMs != null) {
                    val nextStart = lyrics.lines.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE
                    pbState.positionMs in (line.startMs!! until nextStart)
                } else false

                Text(
                    text = line.text,
                    textAlign = TextAlign.Center,
                    style = if (isHighlighted) MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.caption1,
                    color = if (isHighlighted) MaterialTheme.colors.primary else Color.LightGray,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun SettingsScreen(viewModel: AppViewModel, account: AccountUi?) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (account != null) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("当前用户", style = MaterialTheme.typography.caption2, color = Color.Gray)
                    Text(account.userName, style = MaterialTheme.typography.body2)
                    Spacer(Modifier.height(4.dp))
                    Text("服务器", style = MaterialTheme.typography.caption2, color = Color.Gray)
                    Text(account.serverUrl, style = MaterialTheme.typography.caption2, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.requestConfirmation(ConfirmAction.SWITCH_SERVER) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("切换服务器")
            }
        }

        item {
            Button(
                onClick = { viewModel.requestConfirmation(ConfirmAction.LOGOUT) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF6B1B1B))
            ) {
                Text("退出登录", color = Color.White)
            }
        }
    }
}

@Composable
internal fun ConfirmScreen(viewModel: AppViewModel, action: ConfirmAction) {
    val title = when (action) {
        ConfirmAction.CLEAR_QUEUE -> "确定清空播放队列？"
        ConfirmAction.SWITCH_SERVER -> "确定切换服务器？\n将清除当前登录"
        ConfirmAction.LOGOUT -> "确定退出登录？"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.goBack() },
                modifier = Modifier.size(44.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("否")
            }
            Button(
                onClick = { viewModel.confirm(action) },
                modifier = Modifier.size(44.dp),
                colors = ButtonDefaults.primaryButtonColors()
            ) {
                Text("是")
            }
        }
    }
}
