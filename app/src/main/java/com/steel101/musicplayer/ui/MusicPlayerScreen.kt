@file:OptIn(UnstableApi::class)
package com.steel101.musicplayer.ui

import android.content.ContentUris
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.steel101.musicplayer.data.PlaylistEntity
import com.steel101.musicplayer.data.Song
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MusicPlayerScreen(viewModel: MusicViewModel) {
    val songs by viewModel.filteredSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentSong by viewModel.currentSong
    val isPlaying by viewModel.isPlaying
    val currentView by viewModel.currentView.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val selectedDecade by viewModel.selectedDecade.collectAsState()
    val dominantColor by viewModel.dominantColor
    val isOnline by viewModel.isOnlineMode.collectAsState()
    val glassEffectEnabled by viewModel.glassEffectEnabled.collectAsState()

    val animatedBgColor by animateColorAsState(
        targetValue = Color(dominantColor).copy(alpha = 0.98f),
        animationSpec = tween(durationMillis = 1000),
        label = "bgColorTransition"
    )

    val playlists by viewModel.playlists.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedSongs by viewModel.selectedSongs.collectAsState()
    val pendingWriteRequest by viewModel.pendingWriteRequest
    val songListState = rememberLazyListState()
    val artistGridState = rememberLazyGridState()
    val albumGridState = rememberLazyGridState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showQueueSheet by remember { mutableStateOf(false) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                viewModel.onSongDeleted()
                viewModel.loadSongs()
            }
            viewModel.consumePendingWriteRequest()
            viewModel.consumePendingDeleteRequest()
        }
    )

    val pendingDeleteRequest by viewModel.pendingDeleteRequest

    LaunchedEffect(pendingWriteRequest, pendingDeleteRequest) {
        pendingWriteRequest?.let { pendingIntent ->
            intentSenderLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(pendingIntent).build()
            )
        }
        pendingDeleteRequest?.let { pendingIntent ->
            intentSenderLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(pendingIntent).build()
            )
        }
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var expandedLibrary by remember { mutableStateOf(false) }
    var expandedStats by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var songToFix by remember { mutableStateOf<Song?>(null) }
    var songOptions by remember { mutableStateOf<Song?>(null) }
    var artistToEdit by remember { mutableStateOf<ArtistInfo?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showArtistBio by remember { mutableStateOf<String?>(null) }
    var songToEditGenre by remember { mutableStateOf<Song?>(null) }

    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && artistToEdit != null) {
                viewModel.updateArtistImage(artistToEdit!!.name, uri.toString())
                artistToEdit = null
            }
        }
    )

    var songToEditArtwork by remember { mutableStateOf<Song?>(null) }
    var albumToEditArtwork by remember { mutableStateOf<AlbumInfo?>(null) }
    var showFullResolutionArt by remember { mutableStateOf<Any?>(null) }

    val songArtworkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && songToEditArtwork != null) {
                viewModel.updateSongArtwork(songToEditArtwork!!, uri.toString())
                songToEditArtwork = null
            }
        }
    )

    val albumArtworkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && albumToEditArtwork != null) {
                viewModel.updateAlbumArtwork(albumToEditArtwork!!, uri.toString())
                albumToEditArtwork = null
            }
        }
    )

    BackHandler(enabled = isSearchActive || showFullPlayer || isSelectionMode || showFullResolutionArt != null || currentView != MusicViewModel.View.SONGS) {
        if (showFullResolutionArt != null) {
            showFullResolutionArt = null
        } else if (isSelectionMode) {
            viewModel.clearSelection()
        } else if (isSearchActive) {
            isSearchActive = false
            viewModel.setSearchQuery("")
        } else if (showFullPlayer) {
            showFullPlayer = false
        } else {
            when (currentView) {
                MusicViewModel.View.ARTIST_DETAIL -> viewModel.setView(MusicViewModel.View.ARTISTS)
                MusicViewModel.View.ALBUM_DETAIL -> viewModel.setView(MusicViewModel.View.ALBUMS)
                MusicViewModel.View.GENRE_DETAIL -> viewModel.setView(MusicViewModel.View.GENRES)
                MusicViewModel.View.PLAYLIST_DETAIL -> viewModel.setView(MusicViewModel.View.PLAYLISTS)
                MusicViewModel.View.DECADE_DETAIL -> viewModel.setView(MusicViewModel.View.DECADES)
                MusicViewModel.View.DECADES -> viewModel.setView(MusicViewModel.View.SONGS)
                MusicViewModel.View.ARTISTS, MusicViewModel.View.ALBUMS, MusicViewModel.View.GENRES,
                MusicViewModel.View.FAVORITES, MusicViewModel.View.PLAYLISTS,
                MusicViewModel.View.RECENTLY_PLAYED, MusicViewModel.View.MOST_PLAYED,
                MusicViewModel.View.RECENTLY_ADDED, MusicViewModel.View.NEVER_PLAYED,
                MusicViewModel.View.FORGOTTEN_FAVORITES,
                MusicViewModel.View.PODCASTS,
                MusicViewModel.View.EQUALIZER, MusicViewModel.View.SETTINGS,
                MusicViewModel.View.QUEUE, MusicViewModel.View.YT_SEARCH,
                MusicViewModel.View.DISCOVER,
                MusicViewModel.View.TAG_EDITOR,
                MusicViewModel.View.MUSIC_QUIZ,
                MusicViewModel.View.TRIMMER,
                MusicViewModel.View.SMART_PLAYLIST_BUILDER,
                MusicViewModel.View.ABOUT -> viewModel.setView(MusicViewModel.View.SONGS)

                else -> {}
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (glassEffectEnabled) Color.Black.copy(alpha = 0.85f) else Color(0xFF1A1A1A),
                drawerContentColor = Color.Yellow,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .border(
                        BorderStroke(
                            0.5.dp,
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.2f)))
                        ),
                        RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(48.dp))
                    
                    val navigationItems = listOf(
                        Triple(viewModel.translate("All Songs"), MusicViewModel.View.SONGS, AppIcons.MusicNote),
                        Triple(viewModel.translate("Discover"), MusicViewModel.View.DISCOVER, Icons.Default.AutoAwesome),
                        Triple(viewModel.translate("Music Quiz"), MusicViewModel.View.MUSIC_QUIZ, Icons.Default.Extension),
                        Triple(viewModel.translate("Artists"), MusicViewModel.View.ARTISTS, Icons.Default.Person),
                        Triple(viewModel.translate("Albums"), MusicViewModel.View.ALBUMS, AppIcons.Album)
                    )

                    navigationItems.forEach { (label, view, icon) ->
                        NavigationDrawerItem(
                            label = { Text(label, fontWeight = FontWeight.Bold) },
                            selected = currentView == view,
                            onClick = {
                                viewModel.setView(view)
                                showFullPlayer = false
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(icon, null) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color.Yellow.copy(alpha = 0.15f),
                                unselectedContainerColor = Color.Transparent,
                                selectedIconColor = Color.Yellow,
                                unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                selectedTextColor = Color.Yellow,
                                unselectedTextColor = Color.White.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.padding(vertical = 2.dp).clip(RoundedCornerShape(16.dp))
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))

                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Library"), fontWeight = FontWeight.Bold) },
                        selected = false,
                        onClick = { expandedLibrary = !expandedLibrary },
                        icon = { Icon(if (expandedLibrary) AppIcons.ExpandLess else AppIcons.ExpandMore, null) },
                        badge = { Icon(AppIcons.LibraryMusic, null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                        )
                    )

                    AnimatedVisibility(visible = expandedLibrary) {
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            listOf(
                                Triple(viewModel.translate("Genres"), MusicViewModel.View.GENRES, AppIcons.Label),
                                Triple(viewModel.translate("Folders"), MusicViewModel.View.FOLDERS, AppIcons.Folder),
                                Triple(viewModel.translate("Decades"), MusicViewModel.View.DECADES, AppIcons.Event)
                            ).forEach { (label, view, icon) ->
                                NavigationDrawerItem(
                                    label = { Text(label) },
                                    selected = currentView == view,
                                    onClick = {
                                        viewModel.setView(view)
                                        showFullPlayer = false
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Icon(icon, null) },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = Color.Yellow.copy(alpha = 0.1f),
                                        unselectedContainerColor = Color.Transparent,
                                        selectedTextColor = Color.Yellow,
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }

                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Podcasts")) },
                        selected = currentView == MusicViewModel.View.PODCASTS,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.PODCASTS)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(AppIcons.Podcasts, null) }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Favorites")) },
                        selected = currentView == MusicViewModel.View.FAVORITES,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.FAVORITES)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Favorite, null) }
                    )
                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Playlists")) },
                        selected = currentView == MusicViewModel.View.PLAYLISTS,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.PLAYLISTS)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(AppIcons.PlaylistPlay, null) }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = viewModel.translate("Smart Mixes"),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Yellow.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Vibe: Energetic")) },
                        selected = selectedPlaylist?.name == "Vibe: Energetic",
                        onClick = {
                            viewModel.setView(MusicViewModel.View.PLAYLIST_DETAIL, playlist = PlaylistEntity(-4, "Vibe: Energetic"))
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Star, null, tint = Color.Yellow) }
                    )
                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Vibe: Chill")) },
                        selected = selectedPlaylist?.name == "Vibe: Chill",
                        onClick = {
                            viewModel.setView(MusicViewModel.View.PLAYLIST_DETAIL, playlist = PlaylistEntity(-5, "Vibe: Chill"))
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Favorite, null, tint = Color.Cyan) }
                    )
                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Vibe: Party")) },
                        selected = selectedPlaylist?.name == "Vibe: Party",
                        onClick = {
                            viewModel.setView(MusicViewModel.View.PLAYLIST_DETAIL, playlist = PlaylistEntity(-6, "Vibe: Party"))
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Refresh, null, tint = Color.Magenta) }
                    )
                    
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Stats & History")) },
                        selected = false,
                        onClick = { expandedStats = !expandedStats },
                        icon = { Icon(if (expandedStats) AppIcons.ExpandLess else AppIcons.ExpandMore, null) },
                        badge = { Icon(AppIcons.BarChart, null) }
                    )

                    AnimatedVisibility(visible = expandedStats) {
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            NavigationDrawerItem(
                                label = { Text(viewModel.translate("Recently Played")) },
                                selected = currentView == MusicViewModel.View.RECENTLY_PLAYED,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.RECENTLY_PLAYED)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.History, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text(viewModel.translate("Most Played")) },
                                selected = currentView == MusicViewModel.View.MOST_PLAYED,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.MOST_PLAYED)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.BarChart, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text(viewModel.translate("Recently Added")) },
                                selected = currentView == MusicViewModel.View.RECENTLY_ADDED,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.RECENTLY_ADDED)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.NewReleases, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text(viewModel.translate("Never Played")) },
                                selected = currentView == MusicViewModel.View.NEVER_PLAYED,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.NEVER_PLAYED)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.QuestionMark, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text(viewModel.translate("Forgotten Favorites")) },
                                selected = currentView == MusicViewModel.View.FORGOTTEN_FAVORITES,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.FORGOTTEN_FAVORITES)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.HistoryEdu, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text(viewModel.translate("Top Played")) },
                                selected = currentView == MusicViewModel.View.MOST_PLAYED,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.MOST_PLAYED)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.Star, null) }
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Insights")) },
                        selected = currentView == MusicViewModel.View.INSIGHTS,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.INSIGHTS)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(AppIcons.Insights, null) }
                    )

                    if (isOnline) {
                        NavigationDrawerItem(
                            label = { Text(viewModel.translate("YouTube Search")) },
                            selected = currentView == MusicViewModel.View.YT_SEARCH,
                            onClick = {
                                viewModel.setView(MusicViewModel.View.YT_SEARCH)
                                showFullPlayer = false
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Default.Search, null) }
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    NavigationDrawerItem(
                        label = { Text(viewModel.translate("Settings")) },
                        selected = currentView == MusicViewModel.View.SETTINGS,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.SETTINGS)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Settings, null) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (currentSong != null && currentView != MusicViewModel.View.MUSIC_QUIZ) {
                val albumArtUri = if (currentSong!!.hasEmbeddedArt) {
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), currentSong!!.albumId)
                } else {
                    currentSong!!.albumImageUrl?.let { Uri.parse(it) }
                }

                if (glassEffectEnabled) {
                    AsyncImage(
                        model = albumArtUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = if (showFullPlayer) 0.6f else 0.45f)
                            .blur(radius = 12.dp),
                        contentScale = ContentScale.Crop,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix().apply {
                                setToSaturation(1.5f)
                            }
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = if (showFullPlayer) 0.15f else 0.2f),
                                        animatedBgColor.copy(alpha = if (showFullPlayer) 0.25f else 0.35f),
                                        Color.Black.copy(alpha = if (showFullPlayer) 0.5f else 0.6f)
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }
            }

            SharedTransitionLayout {
                val sharedTransitionScope = this
                AnimatedContent(
                    targetState = showFullPlayer,
                    transitionSpec = {
                        if (targetState) {
                            slideInVertically(animationSpec = tween(600)) { it } + fadeIn(
                                animationSpec = tween(600)
                            ) togetherWith
                                    fadeOut(animationSpec = tween(600))
                        } else {
                            fadeIn(animationSpec = tween(600)) togetherWith
                                    slideOutVertically(animationSpec = tween(600)) { it } + fadeOut(
                                animationSpec = tween(600)
                            )
                        }
                    },
                    label = "playerTransition"
                ) { isFullPlayerVisible ->
                    val animatedVisibilityScope = this
                    if (isFullPlayerVisible) {
                        currentSong?.let { song ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .swipeToDismiss { showFullPlayer = false }
                            ) {
                                FullPlayerView(
                                    song = song,
                                    isPlaying = isPlaying,
                                    viewModel = viewModel,
                                    onTogglePlayPause = { viewModel.togglePlayPause() },
                                    onDismiss = { showFullPlayer = false },
                                    onShowQueue = { showQueueSheet = true },
                                    onShowArtistBio = { 
                                        viewModel.fetchArtistInfo(it)
                                        showArtistBio = it
                                    },
                                    onShowFullResolutionArt = { showFullResolutionArt = it },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                    } else {
                        val yellowColorScheme = MaterialTheme.colorScheme.copy(
                            primary = Color.Yellow,
                            onPrimary = Color.Black,
                            primaryContainer = Color.Yellow.copy(alpha = 0.2f),
                            onPrimaryContainer = Color.Yellow,
                            secondary = Color.Yellow,
                            onSecondary = Color.Black,
                            secondaryContainer = Color.Yellow.copy(alpha = 0.2f),
                            onSecondaryContainer = Color.Yellow,
                            surface = Color.DarkGray,
                            onSurface = Color.Yellow,
                            onSurfaceVariant = Color.Yellow.copy(alpha = 0.7f),
                            surfaceVariant = Color.Black.copy(alpha = 0.2f),
                            outline = Color.Yellow.copy(alpha = 0.5f)
                        )

                        MaterialTheme(colorScheme = yellowColorScheme) {
                            Scaffold(
                                containerColor = Color.Transparent, 
                                topBar = {
                                if (isSelectionMode) {
                                    var showBatchGenre by remember { mutableStateOf(false) }
                                    if (showBatchGenre) {
                                        GenreEditDialog(
                                            song = Song(0, "", "", "", 0, Uri.EMPTY, 0), 
                                            viewModel = viewModel,
                                            onSave = { viewModel.batchSetGenre(it); showBatchGenre = false },
                                            onDismiss = { showBatchGenre = false }
                                        )
                                    }
                                    TopAppBar(
                                        title = { Text("${selectedSongs.size} selected") },
                                        navigationIcon = {
                                            IconButton(onClick = { viewModel.clearSelection() }) {
                                                Icon(Icons.Default.Close, "Clear Selection")
                                            }
                                        },
                                        actions = {
                                            IconButton(onClick = { showBatchGenre = true }) {
                                                Icon(AppIcons.Label, "Batch Set Genre")
                                            }
                                            IconButton(onClick = { viewModel.batchDelete() }) {
                                                Icon(Icons.Default.Delete, "Batch Delete")
                                            }
                                        }
                                    )
                                } else if (isSearchActive) {
                                    SearchTopAppBar(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.setSearchQuery(it) },
                                        onCloseClick = {
                                            isSearchActive = false
                                            viewModel.setSearchQuery("")
                                        }
                                    )
                                } else {
                                    TopAppBar(
                                        title = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(AppIcons.MusicNote, null, tint = Color.Green, modifier = Modifier.size(24.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = when (currentView) {
                                                        MusicViewModel.View.SONGS -> viewModel.translate("Songs")
                                                        MusicViewModel.View.ARTISTS -> viewModel.translate("Artists")
                                                        MusicViewModel.View.ALBUMS -> viewModel.translate("Albums")
                                                        MusicViewModel.View.ARTIST_DETAIL -> selectedArtist
                                                            ?: viewModel.translate("Artist")

                                                        MusicViewModel.View.ALBUM_DETAIL -> selectedAlbum
                                                            ?: viewModel.translate("Album")

                                                        MusicViewModel.View.GENRE_DETAIL -> selectedGenre
                                                            ?: viewModel.translate("Genre")

                                                        MusicViewModel.View.DECADE_DETAIL -> "${selectedDecade}s"

                                                        MusicViewModel.View.PLAYLIST_DETAIL -> selectedPlaylist?.name
                                                            ?: viewModel.translate("Playlist")

                                                        MusicViewModel.View.GENRES -> viewModel.translate("Genres")
                                                        MusicViewModel.View.FOLDERS -> viewModel.translate("Folders")
                                                        MusicViewModel.View.INSIGHTS -> viewModel.translate("Library Insights")
                                                        MusicViewModel.View.YT_SEARCH -> viewModel.translate("YouTube Search")
                                                        MusicViewModel.View.DISCOVER -> viewModel.translate("Discover")
                                                        MusicViewModel.View.TAG_EDITOR -> viewModel.translate("Full Tag Editor")
                                                        MusicViewModel.View.MUSIC_QUIZ -> viewModel.translate("Music Quiz")
                                                        MusicViewModel.View.SMART_PLAYLIST_BUILDER -> viewModel.translate("Smart Playlist Builder")
                                                        MusicViewModel.View.TRIMMER -> viewModel.translate("Audio Trimmer")

                                                        MusicViewModel.View.FAVORITES -> viewModel.translate("Favorites")
                                                        MusicViewModel.View.PLAYLISTS -> viewModel.translate("Playlists")
                                                        MusicViewModel.View.RECENTLY_PLAYED -> viewModel.translate("Recently Played")
                                                        MusicViewModel.View.MOST_PLAYED -> viewModel.translate("Most Played")
                                                        MusicViewModel.View.RECENTLY_ADDED -> viewModel.translate("Recently Added")
                                                        MusicViewModel.View.NEVER_PLAYED -> viewModel.translate("Never Played")
                                                        MusicViewModel.View.FORGOTTEN_FAVORITES -> viewModel.translate("Forgotten Favorites")
                                                        MusicViewModel.View.PODCASTS -> viewModel.translate("Podcasts")
                                                        MusicViewModel.View.EQUALIZER -> viewModel.translate("Equalizer")
                                                        MusicViewModel.View.SETTINGS -> viewModel.translate("Settings")
                                                        MusicViewModel.View.ABOUT -> viewModel.translate("About")
                                                        MusicViewModel.View.QUEUE -> viewModel.translate("Up Next")
                                                        else -> "Music Player"
                                                    },
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        },
                                        navigationIcon = {
                                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                                Icon(
                                                    Icons.Default.Menu,
                                                    contentDescription = "Menu"
                                                )
                                            }
                                        },
                                        actions = {
                                            if (currentView != MusicViewModel.View.SETTINGS && 
                                                currentView != MusicViewModel.View.ABOUT &&
                                                currentView != MusicViewModel.View.INSIGHTS && 
                                                currentView != MusicViewModel.View.EQUALIZER && 
                                                currentView != MusicViewModel.View.YT_SEARCH &&
                                                currentView != MusicViewModel.View.ARTISTS &&
                                                currentView != MusicViewModel.View.ALBUMS) {
                                                
                                                var showSortMenu by remember { mutableStateOf(false) }
                                                Box {
                                                    IconButton(onClick = { showSortMenu = true }) {
                                                        Icon(AppIcons.Sort, "Sort", tint = Color.Yellow)
                                                    }
                                                    MaterialTheme(
                                                        colorScheme = MaterialTheme.colorScheme.copy(
                                                            surface = Color.DarkGray,
                                                            onSurface = Color.Yellow
                                                        )
                                                    ) {
                                                        DropdownMenu(
                                                            expanded = showSortMenu,
                                                            onDismissRequest = { showSortMenu = false },
                                                            modifier = Modifier
                                                                .background(
                                                                    Brush.verticalGradient(
                                                                        listOf(
                                                                            Color.Black.copy(alpha = 0.9f),
                                                                            Color.DarkGray.copy(alpha = 0.95f)
                                                                        )
                                                                    ),
                                                                    RoundedCornerShape(12.dp)
                                                                )
                                                                .border(0.5.dp, Color.Yellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                                        ) {
                                                            MusicViewModel.SortOrder.entries.forEach { order ->
                                                                DropdownMenuItem(
                                                                    text = { 
                                                                        Text(
                                                                            order.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                                                            color = Color.Yellow
                                                                        ) 
                                                                    },
                                                                    onClick = {
                                                                        viewModel.setSortOrder(order)
                                                                        showSortMenu = false
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                val isRefreshing by viewModel.isRefreshing.collectAsState()
                                                IconButton(onClick = { viewModel.manualRescan() }, enabled = !isRefreshing) {
                                                    if (isRefreshing) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(24.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color.Yellow
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Default.Refresh,
                                                            contentDescription = "Rescan Library",
                                                            tint = Color.Yellow
                                                        )
                                                    }
                                                }

                                                IconButton(onClick = { isSearchActive = true }) {
                                                    Icon(
                                                        Icons.Default.Search,
                                                        contentDescription = "Search",
                                                        tint = Color.Yellow
                                                    )
                                                }
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = Color.White.copy(alpha = 0.05f),
                                            titleContentColor = Color.White,
                                            navigationIconContentColor = Color.White,
                                            actionIconContentColor = Color.White
                                        )
                                    )
                                }
                            },
                            bottomBar = {
                                if (currentSong != null && currentView != MusicViewModel.View.MUSIC_QUIZ) {
                                    PlaybackControls(
                                        song = currentSong!!,
                                        isPlaying = isPlaying,
                                        onTogglePlayPause = { viewModel.togglePlayPause() },
                                        onClick = { showFullPlayer = true },
                                        viewModel = viewModel,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            }
                        ) { padding ->
                            Box(modifier = Modifier.padding(padding)) {
                                when (currentView) {
                                    MusicViewModel.View.SONGS, MusicViewModel.View.FAVORITES,
                                    MusicViewModel.View.ARTIST_DETAIL, MusicViewModel.View.ALBUM_DETAIL,
                                    MusicViewModel.View.GENRE_DETAIL, MusicViewModel.View.DECADE_DETAIL,
                                    MusicViewModel.View.PLAYLIST_DETAIL,
                                    MusicViewModel.View.RECENTLY_PLAYED, MusicViewModel.View.MOST_PLAYED,
                                    MusicViewModel.View.RECENTLY_ADDED, MusicViewModel.View.NEVER_PLAYED,
                                    MusicViewModel.View.FORGOTTEN_FAVORITES,
                                    MusicViewModel.View.PODCASTS -> {
                                        val availableLetters = remember(songs) {
                                            val lettersSet = mutableSetOf<Char>()
                                            var hasHash = false
                                            songs.forEach { song ->
                                                val firstChar =
                                                    song.title.firstOrNull()?.uppercaseChar()
                                                if (firstChar != null) {
                                                    if (firstChar.isLetter()) {
                                                        lettersSet.add(firstChar)
                                                    } else {
                                                        hasHash = true
                                                    }
                                                }
                                            }
                                            val sortedLetters = lettersSet.sorted()
                                            if (hasHash) sortedLetters + '#' else sortedLetters
                                        }

                                        LaunchedEffect(currentSong) {
                                            val index = songs.indexOfFirst { it.id == currentSong?.id }
                                            if (index != -1) {
                                                songListState.animateScrollToItem(index)
                                            }
                                        }

                                        Column(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                            detectVerticalDragGestures { _, dragAmount ->
                                                if (dragAmount > 50 && !isSearchActive) {
                                                    isSearchActive = true
                                                }
                                            }
                                        }) {
                                            val headerTitle = when (currentView) {
                                                MusicViewModel.View.ARTIST_DETAIL -> selectedArtist
                                                MusicViewModel.View.ALBUM_DETAIL -> selectedAlbum
                                                MusicViewModel.View.PLAYLIST_DETAIL -> selectedPlaylist?.name
                                                MusicViewModel.View.GENRE_DETAIL -> selectedGenre
                                                MusicViewModel.View.DECADE_DETAIL -> "${selectedDecade}s"
                                                MusicViewModel.View.FAVORITES -> "Your Favorites"
                                                MusicViewModel.View.RECENTLY_PLAYED -> "Recently Played"
                                                MusicViewModel.View.MOST_PLAYED -> "Most Played"
                                                MusicViewModel.View.RECENTLY_ADDED -> "Recently Added"
                                                MusicViewModel.View.NEVER_PLAYED -> "Never Played"
                                                MusicViewModel.View.FORGOTTEN_FAVORITES -> "Forgotten Favorites"
                                                else -> null
                                            }

                                            val headerImage = when (currentView) {
                                                MusicViewModel.View.ARTIST_DETAIL -> songs.find { it.artistImageUrl != null }?.artistImageUrl
                                                MusicViewModel.View.ALBUM_DETAIL -> {
                                                    val s = songs.firstOrNull()
                                                    if (s?.hasEmbeddedArt == true) {
                                                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.albumId)
                                                    } else s?.albumImageUrl
                                                }
                                                else -> null
                                            }

                                            SongsHeader(
                                                onPlayAll = { viewModel.playSongs(songs) },
                                                onShuffle = { viewModel.playSongs(songs, shuffle = true) },
                                                title = headerTitle,
                                                subtitle = if (songs.isNotEmpty()) "${songs.size} songs" else null,
                                                imageUrl = headerImage,
                                                viewModel = viewModel
                                            )

                                            Spacer(Modifier.height(8.dp))

                                            Row(modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(horizontal = 12.dp)
                                                        .clip(RoundedCornerShape(28.dp))
                                                        .background(
                                                            Brush.verticalGradient(
                                                                listOf(
                                                                    Color.White.copy(alpha = 0.12f),
                                                                    Color.White.copy(alpha = 0.02f)
                                                                )
                                                            )
                                                        )
                                                        .border(
                                                            0.5.dp, 
                                                            Brush.verticalGradient(
                                                                listOf(
                                                                    Color.White.copy(alpha = 0.3f),
                                                                    Color.Transparent
                                                                )
                                                            ), 
                                                            RoundedCornerShape(28.dp)
                                                        )
                                                ) {
                                                    LazyColumn(
                                                        state = songListState,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentPadding = PaddingValues(
                                                            horizontal = 8.dp,
                                                            vertical = 8.dp
                                                        ),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        items(songs) { song ->
                                                            SongItem(
                                                                song = song,
                                                                isSelected = song.id == currentSong?.id,
                                                                isMultiSelected = selectedSongs.contains(song.id),
                                                                onClick = { 
                                                                    if (isSelectionMode) viewModel.toggleSongSelection(song.id)
                                                                    else viewModel.playSong(song, songs) 
                                                                },
                                                                onLongClick = { songOptions = song },
                                                                onArtworkClick = { viewModel.toggleSongSelection(song.id) },
                                                                onFavoriteToggle = {
                                                                    viewModel.toggleFavorite(
                                                                        song
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                val shouldShowSidebar = (
                                                        currentView == MusicViewModel.View.SONGS ||
                                                                currentView == MusicViewModel.View.ARTIST_DETAIL ||
                                                                currentView == MusicViewModel.View.ALBUM_DETAIL ||
                                                                currentView == MusicViewModel.View.GENRE_DETAIL ||
                                                                currentView == MusicViewModel.View.DECADE_DETAIL
                                                        )
                                                if (shouldShowSidebar && songs.size > 5 && availableLetters.isNotEmpty()) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .width(28.dp)
                                                            .padding(vertical = 4.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.SpaceEvenly
                                                    ) {
                                                        availableLetters.forEach { letter ->
                                                            Text(
                                                                text = letter.toString(),
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                ),
                                                                color = Color.White.copy(alpha = 0.7f),
                                                                modifier = Modifier
                                                                    .clickable {
                                                                        val index = if (letter == '#') {
                                                                            songs.indexOfFirst { song ->
                                                                                val char =
                                                                                    song.title.firstOrNull()
                                                                                        ?: ' '
                                                                                !char.isLetter()
                                                                            }
                                                                        } else {
                                                                            songs.indexOfFirst { song ->
                                                                                song.title.startsWith(
                                                                                    letter,
                                                                                    ignoreCase = true
                                                                                )
                                                                            }
                                                                        }
                                                                        if (index != -1) {
                                                                            scope.launch {
                                                                                songListState.scrollToItem(index)
                                                                            }
                                                                        }
                                                                    }
                                                                    .padding(vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    MusicViewModel.View.ARTISTS -> {
                                        val artists = remember(songs) {
                                            songs.groupBy { it.artist.lowercase().trim() }.map { (_, songs) ->
                                                val name = songs.first().artist
                                                val firstWithArtistImage =
                                                    songs.find { it.artistImageUrl != null }
                                                ArtistInfo(
                                                    name,
                                                    firstWithArtistImage?.artistImageUrl,
                                                    songs
                                                )
                                            }.sortedBy { it.name.lowercase() }
                                        }

                                        val availableLetters = remember(artists) {
                                            val lettersSet = mutableSetOf<Char>()
                                            var hasHash = false
                                            artists.forEach { artist ->
                                                val firstChar =
                                                    artist.name.firstOrNull()?.uppercaseChar()
                                                if (firstChar != null) {
                                                    if (firstChar.isLetter()) {
                                                        lettersSet.add(firstChar)
                                                    } else {
                                                        hasHash = true
                                                    }
                                                }
                                            }
                                            val sortedLetters = lettersSet.sorted()
                                            if (hasHash) sortedLetters + '#' else sortedLetters
                                        }

                                        Row(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                            detectVerticalDragGestures { _, dragAmount ->
                                                if (dragAmount > 50 && !isSearchActive) {
                                                    isSearchActive = true
                                                }
                                            }
                                        }) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(12.dp)
                                                    .clip(RoundedCornerShape(28.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                Color.White.copy(alpha = 0.12f),
                                                                Color.White.copy(alpha = 0.02f)
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        0.5.dp, 
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                Color.White.copy(alpha = 0.3f),
                                                                Color.Transparent
                                                            )
                                                        ), 
                                                        RoundedCornerShape(28.dp)
                                                    )
                                            ) {
                                                LazyVerticalGrid(
                                                    state = artistGridState,
                                                    columns = GridCells.Fixed(gridColumns),
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentPadding = PaddingValues(12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    gridItems(artists) { artist ->
                                                        ArtistGridItem(
                                                            artist = artist,
                                                            gridColumns = gridColumns,
                                                            onClick = {
                                                                viewModel.setView(
                                                                    MusicViewModel.View.ARTIST_DETAIL,
                                                                    artist = artist.name
                                                                )
                                                            },
                                                            onLongClick = {
                                                                artistToEdit = artist
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            if (artists.size > 5 && availableLetters.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .width(28.dp)
                                                        .padding(vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.SpaceEvenly
                                                ) {
                                                    availableLetters.forEach { letter ->
                                                        Text(
                                                            text = letter.toString(),
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = Color.Yellow.copy(alpha = 0.7f),
                                                            modifier = Modifier
                                                                .clickable {
                                                                    val index = if (letter == '#') {
                                                                        artists.indexOfFirst { artist ->
                                                                            val char =
                                                                                artist.name.firstOrNull()
                                                                                    ?: ' '
                                                                            !char.isLetter()
                                                                        }
                                                                    } else {
                                                                        artists.indexOfFirst { artist ->
                                                                            artist.name.startsWith(
                                                                                letter,
                                                                                ignoreCase = true
                                                                            )
                                                                        }
                                                                    }
                                                                    if (index != -1) {
                                                                        scope.launch {
                                                                            artistGridState.scrollToItem(
                                                                                index
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                .padding(vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    MusicViewModel.View.ALBUMS -> {
                                        val albums = remember(songs) {
                                            songs.groupBy { it.artist.lowercase().trim() to it.album.lowercase().trim() }.map { (_, songs) ->
                                                val artistName = songs.first().artist
                                                val title = songs.first().album
                                                val firstWithImage =
                                                    songs.find { it.hasEmbeddedArt }
                                                        ?: songs.find { it.albumImageUrl != null }
                                                val imageSource: Any? =
                                                    if (firstWithImage?.hasEmbeddedArt == true) {
                                                        ContentUris.withAppendedId(
                                                            Uri.parse("content://media/external/audio/albumart"),
                                                            firstWithImage.albumId
                                                        )
                                                    } else {
                                                        firstWithImage?.albumImageUrl
                                                    }
                                                AlbumInfo(
                                                    title,
                                                    artistName,
                                                    imageSource,
                                                    songs
                                                )
                                            }.sortedBy { it.title.lowercase() }
                                        }

                                        val availableLetters = remember(albums) {
                                            val lettersSet = mutableSetOf<Char>()
                                            var hasHash = false
                                            albums.forEach { album ->
                                                val firstChar =
                                                    album.title.firstOrNull()?.uppercaseChar()
                                                if (firstChar != null) {
                                                    if (firstChar.isLetter()) {
                                                        lettersSet.add(firstChar)
                                                    } else {
                                                        hasHash = true
                                                    }
                                                }
                                            }
                                            val sortedLetters = lettersSet.sorted()
                                            if (hasHash) sortedLetters + '#' else sortedLetters
                                        }

                                        Row(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                            detectVerticalDragGestures { _, dragAmount ->
                                                if (dragAmount > 50 && !isSearchActive) {
                                                    isSearchActive = true
                                                }
                                            }
                                        }) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(12.dp)
                                                    .clip(RoundedCornerShape(28.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                Color.White.copy(alpha = 0.12f),
                                                                Color.White.copy(alpha = 0.02f)
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        0.5.dp, 
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                Color.White.copy(alpha = 0.3f),
                                                                Color.Transparent
                                                            )
                                                        ), 
                                                        RoundedCornerShape(28.dp)
                                                    )
                                            ) {
                                                LazyVerticalGrid(
                                                    state = albumGridState,
                                                    columns = GridCells.Fixed(gridColumns),
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentPadding = PaddingValues(12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    gridItems(albums) { album ->
                                                        AlbumGridItem(
                                                            album = album,
                                                            gridColumns = gridColumns,
                                                            onClick = {
                                                                viewModel.setView(
                                                                    MusicViewModel.View.ALBUM_DETAIL,
                                                                    artist = album.artist,
                                                                    album = album.title
                                                                )
                                                            },
                                                            onLongClick = {
                                                                albumToEditArtwork = album
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            if (albums.size > 5 && availableLetters.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .width(28.dp)
                                                        .padding(vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.SpaceEvenly
                                                ) {
                                                    availableLetters.forEach { letter ->
                                                        Text(
                                                            text = letter.toString(),
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = Color.Yellow.copy(alpha = 0.7f),
                                                            modifier = Modifier
                                                                .clickable {
                                                                    val index = if (letter == '#') {
                                                                        albums.indexOfFirst { album ->
                                                                            val char =
                                                                                album.title.firstOrNull()
                                                                                    ?: ' '
                                                                            !char.isLetter()
                                                                        }
                                                                    } else {
                                                                        albums.indexOfFirst { album ->
                                                                            album.title.startsWith(
                                                                                letter,
                                                                                ignoreCase = true
                                                                            )
                                                                        }
                                                                    }
                                                                    if (index != -1) {
                                                                        scope.launch {
                                                                            albumGridState.scrollToItem(
                                                                                index
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                .padding(vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    MusicViewModel.View.PLAYLISTS -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp)
                                                .clip(RoundedCornerShape(28.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.12f),
                                                            Color.White.copy(alpha = 0.02f)
                                                        )
                                                    )
                                                )
                                                .border(
                                                    0.5.dp, 
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.3f),
                                                            Color.Transparent
                                                        )
                                                    ), 
                                                    RoundedCornerShape(28.dp)
                                                )
                                        ) {
                                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = { showCreatePlaylistDialog = true },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow.copy(alpha = 0.15f), contentColor = Color.Yellow)
                                                    ) {
                                                        Icon(Icons.Default.Add, null)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(viewModel.translate("Manual"))
                                                    }
                                                    Button(
                                                        onClick = { viewModel.setView(MusicViewModel.View.SMART_PLAYLIST_BUILDER) },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow.copy(alpha = 0.15f), contentColor = Color.Yellow)
                                                    ) {
                                                        Icon(Icons.Default.AutoAwesome, null)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(viewModel.translate("Smart"))
                                                    }
                                                }
                                                Spacer(Modifier.height(16.dp))
                                                LazyColumn {
                                                    items(playlists) { playlist ->
                                                        val isSmart = playlist.id < 0
                                                        PlaylistListItem(
                                                            playlist = playlist,
                                                            onClick = {
                                                                viewModel.setView(
                                                                    MusicViewModel.View.PLAYLIST_DETAIL,
                                                                    playlist = playlist
                                                                )
                                                            },
                                                            onLongClick = {
                                                                if (!isSmart) {
                                                                    playlistToDelete = playlist
                                                                }
                                                            },
                                                            icon = if (isSmart) Icons.Default.AutoAwesome else AppIcons.PlaylistPlay
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    MusicViewModel.View.EQUALIZER -> {
                                        EqualizerView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.SETTINGS -> {
                                        SettingsView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.QUEUE -> {
                                        QueueView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.GENRES -> {
                                        val genres = remember(songs) {
                                            songs.groupBy { it.genre ?: "Unknown" }
                                                .map { (name, songs) -> GenreInfo(name, songs) }
                                                .sortedBy { it.name.lowercase() }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp)
                                                .clip(RoundedCornerShape(28.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.12f),
                                                            Color.White.copy(alpha = 0.02f)
                                                        )
                                                    )
                                                )
                                                .border(
                                                    0.5.dp, 
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.3f),
                                                            Color.Transparent
                                                        )
                                                    ), 
                                                    RoundedCornerShape(28.dp)
                                                )
                                        ) {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(genres) { genre ->
                                                    ListItem(
                                                        headlineContent = { Text(genre.name, color = Color.Yellow, fontWeight = FontWeight.Bold) },
                                                        supportingContent = { Text("${genre.songs.size} songs", color = Color.White.copy(alpha = 0.6f)) },
                                                        leadingContent = { Icon(AppIcons.Label, null, tint = Color.Yellow) },
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable {
                                                                viewModel.setView(MusicViewModel.View.GENRE_DETAIL, genre = genre.name)
                                                            },
                                                        colors = ListItemDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f))
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    MusicViewModel.View.DECADES -> {
                                        val decades = remember(songs) {
                                            songs.groupBy { if (it.year > 0) (it.year / 10) * 10 else 0 }
                                                .map { (decade, songs) -> decade to songs }
                                                .sortedByDescending { it.first }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp)
                                                .clip(RoundedCornerShape(28.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.12f),
                                                            Color.White.copy(alpha = 0.02f)
                                                        )
                                                    )
                                                )
                                                .border(
                                                    0.5.dp, 
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.3f),
                                                            Color.Transparent
                                                        )
                                                    ), 
                                                    RoundedCornerShape(28.dp)
                                                )
                                        ) {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(decades) { (decade, decadeSongs) ->
                                                    ListItem(
                                                        headlineContent = { Text(if (decade > 0) "${decade}s" else "Unknown Era", color = Color.Yellow, fontWeight = FontWeight.Bold) },
                                                        supportingContent = { Text("${decadeSongs.size} songs", color = Color.White.copy(alpha = 0.6f)) },
                                                        leadingContent = { Icon(AppIcons.Event, null, tint = Color.Yellow) },
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable {
                                                                viewModel.setView(MusicViewModel.View.DECADE_DETAIL, decade = decade)
                                                            },
                                                        colors = ListItemDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f))
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    MusicViewModel.View.FOLDERS -> {
                                        FolderBrowserView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.INSIGHTS -> {
                                        InsightsView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.YT_SEARCH -> {
                                        YouTubeSearchView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.DISCOVER -> {
                                        DiscoverView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.TAG_EDITOR -> {
                                        TagEditorView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.MUSIC_QUIZ -> {
                                        MusicQuizView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.SMART_PLAYLIST_BUILDER -> {
                                        SmartPlaylistBuilderView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.TRIMMER -> {
                                        AudioTrimmerView(viewModel = viewModel)
                                    }

                                    MusicViewModel.View.ABOUT -> {
                                        AboutView(viewModel = viewModel)
                                    }

                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }

                val isVideoMode by viewModel.isVideoMode
                
                if (isVideoMode) {
                    YouTubeVideoPlayer(viewModel = viewModel, onDismiss = { viewModel.stop() })
                }

                if (songToFix != null) {
                    FixMetadataDialog(
                        song = songToFix!!,
                        viewModel = viewModel,
                        onDismiss = { songToFix = null }
                    )
                }

                if (songToEditGenre != null) {
                    GenreEditDialog(
                        song = songToEditGenre!!,
                        viewModel = viewModel,
                        onSave = { genre ->
                            viewModel.saveGenre(songToEditGenre!!, genre)
                            songToEditGenre = null
                        },
                        onDismiss = { songToEditGenre = null }
                    )
                }

                if (songOptions != null) {
                    SongOptionsDialog(
                        song = songOptions!!,
                        viewModel = viewModel,
                        onFixMetadata = {
                            songToFix = songOptions
                            songOptions = null
                        },
                        onSetArtwork = {
                            songToEditArtwork = songOptions
                            songOptions = null
                        },
                        onAddToPlaylist = {
                            showPlaylistDialog = songOptions
                            songOptions = null
                        },
                        onPlayNext = {
                            viewModel.playNext(songOptions!!)
                            songOptions = null
                        },
                        onAddToQueue = {
                            viewModel.addToQueue(songOptions!!)
                            songOptions = null
                        },
                        onToggleNotPodcast = {
                            viewModel.toggleNotPodcast(songOptions!!)
                            songOptions = null
                        },
                        onGoToArtist = {
                            viewModel.setView(MusicViewModel.View.ARTIST_DETAIL, artist = songOptions!!.artist)
                            songOptions = null
                        },
                        onGoToAlbum = {
                            viewModel.setView(MusicViewModel.View.ALBUM_DETAIL, artist = songOptions!!.artist, album = songOptions!!.album)
                            songOptions = null
                        },
                        onIdentifySong = {
                            viewModel.identifySong(songOptions!!)
                            songOptions = null
                        },
                        onSetGenre = {
                            songToEditGenre = songOptions
                            songOptions = null
                        },
                        onDelete = {
                            showDeleteConfirm = songOptions
                            songOptions = null
                        },
                        onStartRadio = {
                            viewModel.startYoutubeRadio(songOptions!!)
                            songOptions = null
                        },
                        onDismiss = { songOptions = null },
                        isOnline = isOnline
                    )
                }

                if (showDeleteConfirm != null) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = null },
                        title = { Text("Delete Song") },
                        text = { Text("Are you sure you want to delete '${showDeleteConfirm!!.title}' from your device? This cannot be undone.") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.deleteSong(showDeleteConfirm!!)
                                showDeleteConfirm = null
                            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
                        }
                    )
                }

                if (songToEditArtwork != null) {
                    SongArtworkDialog(
                        song = songToEditArtwork!!,
                        onPickFromGallery = {
                            songArtworkPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSetUrl = { url ->
                            viewModel.updateSongArtwork(songToEditArtwork!!, url)
                            songToEditArtwork = null
                        },
                        onDismiss = { songToEditArtwork = null },
                        isOnline = isOnline
                    )
                }

                if (albumToEditArtwork != null) {
                    AlbumArtworkDialog(
                        album = albumToEditArtwork!!,
                        onPickFromGallery = {
                            albumArtworkPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSetUrl = { url ->
                            viewModel.updateAlbumArtwork(albumToEditArtwork!!, url)
                            albumToEditArtwork = null
                        },
                        onDismiss = { albumToEditArtwork = null },
                        isOnline = isOnline
                    )
                }

                if (showPlaylistDialog != null) {
                    AddToPlaylistDialog(
                        playlists = playlists,
                        viewModel = viewModel,
                        onPlaylistSelected = { playlistId ->
                            viewModel.addSongToPlaylist(playlistId, showPlaylistDialog!!)
                            showPlaylistDialog = null
                        },
                        onDismiss = { showPlaylistDialog = null }
                    )
                }

                if (showCreatePlaylistDialog) {
                    var name by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showCreatePlaylistDialog = false },
                        title = { Text(viewModel.translate("New Playlist")) },
                        text = {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(viewModel.translate("Playlist Name")) },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (name.isNotBlank()) viewModel.createPlaylist(name)
                                showCreatePlaylistDialog = false
                            }) { Text(viewModel.translate("Create")) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showCreatePlaylistDialog = false
                            }) { Text(viewModel.translate("Cancel")) }
                        }
                    )
                }

                if (playlistToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { playlistToDelete = null },
                        title = { Text("Delete Playlist") },
                        text = { Text("Are you sure you want to delete '${playlistToDelete!!.name}'?") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.deletePlaylist(playlistToDelete!!)
                                playlistToDelete = null
                            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") }
                        }
                    )
                }

                if (artistToEdit != null) {
                    ArtistImageDialog(
                        artist = artistToEdit!!,
                        onPickFromGallery = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSetUrl = { url ->
                            viewModel.updateArtistImage(artistToEdit!!.name, url)
                            artistToEdit = null
                        },
                        onDismiss = { artistToEdit = null },
                        isOnline = isOnline
                    )
                }

                if (showArtistBio != null) {
                    ArtistBioDialog(
                        artistName = showArtistBio!!,
                        viewModel = viewModel,
                        onDismiss = { showArtistBio = null }
                    )
                }

                val showOnboarding by viewModel.showOnlineOnboarding.collectAsState()
                if (showOnboarding) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissOnlineOnboarding() },
                        title = { Text("Online Features") },
                        text = {
                            Text("This app can connect to the internet to fetch album art, lyrics, artist bios, and identify songs. Would you like to enable these features or stay offline?")
                        },
                        confirmButton = {
                            Button(onClick = { viewModel.setOnlineMode(true) }) {
                                Text("Enable Online Features")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.setOnlineMode(false) }) {
                                Text("Stay Offline")
                            }
                        }
                    )
                }

                if (showQueueSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showQueueSheet = false },
                        sheetState = sheetState,
                        containerColor = if (glassEffectEnabled) Color(dominantColor).copy(alpha = 0.95f) else Color(0xFF121212),
                        contentColor = Color.White
                    ) {
                        QueueView(viewModel = viewModel)
                    }
                }

                if (showFullResolutionArt != null) {
                    FullResolutionArtViewer(
                        imageSource = showFullResolutionArt!!,
                        onDismiss = { showFullResolutionArt = null }
                    )
                }
            }
        }
    }
}
@Composable
fun ArtistBioDialog(
    artistName: String,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val bio by viewModel.artistBio.collectAsState()
    val albums by viewModel.artistDiscography.collectAsState()
    val mbTracks by viewModel.mbAlbumTracks.collectAsState()
    val isLoadingBio by viewModel.isFetchingArtistInfo
    val isLoadingTracks by viewModel.isFetchingMbTracks
    val downloadingTracks by viewModel.downloadingTracks.collectAsState()
    val isOnline by viewModel.isOnlineMode.collectAsState()

    var viewingAlbumTitle by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.85f), Color.DarkGray.copy(alpha = 0.95f))
                )
            )
            .border(
                0.5.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)),
                RoundedCornerShape(28.dp)
            ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (viewingAlbumTitle != null) {
                    IconButton(onClick = { viewingAlbumTitle = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Yellow)
                    }
                }
                Text(
                    viewingAlbumTitle ?: artistName,
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.7f)) {
                if (!isOnline) {
                    Text("Online Features disabled.", color = Color.White.copy(alpha = 0.7f))
                } else if (isLoadingBio || (viewingAlbumTitle != null && isLoadingTracks)) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Yellow)
                    }
                } else if (viewingAlbumTitle != null) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (mbTracks.isEmpty()) {
                            Text("No tracks found.", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
                        } else {
                            mbTracks.forEach { track ->
                                ListItem(
                                    headlineContent = { Text("${track.position}. ${track.title}", color = Color.White) },
                                    supportingContent = {
                                        val localSongs by viewModel.songs.collectAsState()
                                        val inLibrary = localSongs.any {
                                            it.title.equals(track.title, ignoreCase = true) &&
                                                    it.artist.contains(artistName, ignoreCase = true)
                                        }
                                        if (inLibrary) {
                                            Text(viewModel.translate("In Library"), color = Color.Green, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { viewModel.playPreview(track.title ?: "", artistName) }) {
                                                Icon(Icons.Default.PlayArrow, "Preview", tint = Color.Yellow)
                                            }
                                            val localSongs by viewModel.songs.collectAsState()
                                            val inLibrary = localSongs.any {
                                                it.title.equals(track.title, ignoreCase = true) &&
                                                        it.artist.contains(artistName, ignoreCase = true)
                                            }
                                            if (!inLibrary) {
                                                val trackKey = "$artistName - ${track.title}"
                                                val progress = downloadingTracks[trackKey]
                                                if (progress != null) {
                                                    CircularProgressIndicator(progress = { progress!! }, modifier = Modifier.size(24.dp), color = Color.Yellow)
                                                } else {
                                                    IconButton(onClick = { viewModel.downloadFromYoutube(track.title ?: "", artistName) }) {
                                                        Icon(AppIcons.Download, null, tint = Color.Yellow)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (bio != null) {
                            Text(viewModel.translate("Biography"), color = Color.Yellow, fontWeight = FontWeight.Bold)
                            Text(bio!!, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(vertical = 8.dp))
                        }
                        if (albums.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(viewModel.translate("Discography"), color = Color.Yellow, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            albums.forEach { album ->
                                ListItem(
                                    headlineContent = { Text(album.title ?: "Unknown", color = Color.White) },
                                    supportingContent = { Text(album.year ?: "", color = Color.White.copy(alpha = 0.6f)) },
                                    leadingContent = {
                                        AsyncImage(
                                            model = album.thumbUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        album.title?.let {
                                            viewingAlbumTitle = it
                                            viewModel.fetchAlbumTracksFromMusicBrainz(it, artistName)
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f))
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(viewModel.translate("Close"), color = Color.Yellow) }
        }
    )
}

@OptIn(UnstableApi::class)
@Composable
fun YouTubeSearchView(viewModel: MusicViewModel) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.ytSearchResults.collectAsState()
    val isSearching by viewModel.isSearchingYt
    val downloadingTracks by viewModel.downloadingTracks.collectAsState()
    
    var pendingDownloadItem by remember<MutableState<StreamInfoItem?>> { mutableStateOf(null) }
    var downloadAsVideo by remember { mutableStateOf(false) }

    if (pendingDownloadItem != null) {
        AlertDialog(
            onDismissRequest = { pendingDownloadItem = null },
            title = { Text(viewModel.translate("Download Options")) },
            text = { 
                Column {
                    Text(viewModel.translate("Download") + " ${pendingDownloadItem?.name}")
                    Spacer(Modifier.height(8.dp))
                    if (downloadAsVideo) {
                        Text(
                            viewModel.translate("WARNING: You are about to download this as a VIDEO file, not just a song. This will take more space."),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDownloadItem?.let { item ->
                            viewModel.downloadFromYoutube(item.name, item.uploaderName ?: "Unknown", isVideo = downloadAsVideo)
                        }
                        pendingDownloadItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black)
                ) {
                    Text(viewModel.translate("Download"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownloadItem = null }) {
                    Text(viewModel.translate("Cancel"), color = Color.White)
                }
            },
            containerColor = Color(0xFF1C1B1F),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val searchSourceMusic by viewModel.ytSearchSourceMusic.collectAsState()

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(viewModel.translate("Search YouTube")) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Yellow,
                unfocusedTextColor = Color.Yellow,
                focusedLabelColor = Color.Yellow,
                unfocusedLabelColor = Color.Yellow.copy(alpha = 0.7f),
                focusedBorderColor = Color.Yellow,
                unfocusedBorderColor = Color.Yellow.copy(alpha = 0.5f),
                cursorColor = Color.Yellow
            ),
            trailingIcon = {
                IconButton(onClick = { viewModel.searchYoutube(query) }) {
                    Icon(Icons.Default.Search, null, tint = Color.Yellow)
                }
            },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(viewModel.translate("Search Mode"), color = Color.Yellow.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "YouTube", 
                    color = if (!searchSourceMusic) Color.Yellow else Color.Gray, 
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (!searchSourceMusic) FontWeight.Bold else FontWeight.Normal
                )
                Switch(
                    checked = searchSourceMusic,
                    onCheckedChange = { viewModel.setYtSearchSourceMusic(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Yellow,
                        checkedTrackColor = Color.Yellow.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text(
                    text = "Music", 
                    color = if (searchSourceMusic) Color.Yellow else Color.Gray, 
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (searchSourceMusic) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))
                    )
                )
                .border(
                    0.5.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)),
                    RoundedCornerShape(28.dp)
                )
        ) {
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Yellow)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(results) { item ->
                        val trackKey = "${item.uploaderName} - ${item.name}"
                        val progress = downloadingTracks[trackKey]
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = item.thumbnails?.firstOrNull()?.url,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                        placeholder = rememberVectorPainter(AppIcons.MusicNote),
                                        error = rememberVectorPainter(AppIcons.MusicNote)
                                    )
                                    
                                    Spacer(Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name, 
                                            color = Color.White, 
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2, 
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = item.uploaderName ?: "Unknown Artist", 
                                            color = Color.White.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.playYoutubePreview(item) }) {
                                        Icon(Icons.Default.PlayArrow, null, tint = Color.Yellow)
                                    }
                                    
                                    if (searchSourceMusic) {
                                        IconButton(onClick = { viewModel.startYoutubeRadio(item) }) {
                                            Icon(Icons.Default.Radio, null, tint = Color.Yellow)
                                        }
                                    }
                                    
                                    if (progress != null) {
                                        CircularProgressIndicator(
                                            progress = { progress!! }, 
                                            modifier = Modifier.size(24.dp), 
                                            color = Color.Yellow
                                        )
                                    } else {
                                        if (searchSourceMusic) {
                                            IconButton(onClick = { 
                                                downloadAsVideo = false
                                                pendingDownloadItem = item 
                                            }) {
                                                Icon(AppIcons.Download, null, tint = Color.Yellow)
                                            }
                                        }
                                        IconButton(onClick = { 
                                            downloadAsVideo = true
                                            pendingDownloadItem = item 
                                        }) {
                                            Icon(Icons.Default.Movie, null, tint = Color.Yellow)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverView(viewModel: MusicViewModel) {
    val discoveryResults by viewModel.discoveryResults.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val downloadingTracks by viewModel.downloadingTracks.collectAsState()

    LaunchedEffect(Unit) {
        if (discoveryResults.isEmpty()) {
            viewModel.discoverMusic()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = viewModel.translate("Discover New Music"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isDiscovering && discoveryResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Yellow)
            }
        } else {
            discoveryResults.forEach { (sectionTitle, items) ->
                DiscoverSection(
                    title = viewModel.translate(sectionTitle),
                    items = items,
                    viewModel = viewModel,
                    downloadingTracks = downloadingTracks
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun DiscoverSection(
    title: String,
    items: List<StreamInfoItem>,
    viewModel: MusicViewModel,
    downloadingTracks: Map<String, Float>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(items) { item ->
                DiscoverItemCard(
                    item = item,
                    viewModel = viewModel,
                    downloadingTracks = downloadingTracks
                )
            }
        }
    }
}

@Composable
fun DiscoverItemCard(
    item: StreamInfoItem,
    viewModel: MusicViewModel,
    downloadingTracks: Map<String, Float>
) {
    val trackKey = "${item.uploaderName} - ${item.name}"
    val progress = downloadingTracks[trackKey]

    Card(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { viewModel.playYoutubePreview(item, forceAudio = true) },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column {
            Box(modifier = Modifier.height(160.dp).fillMaxWidth()) {
                AsyncImage(
                    model = item.thumbnails?.firstOrNull()?.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(AppIcons.MusicNote),
                    error = rememberVectorPainter(AppIcons.MusicNote)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }

                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress!! },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).size(24.dp),
                        color = Color.Yellow
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.uploaderName ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { viewModel.startYoutubeRadio(item, forceAudio = true) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Radio, null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { viewModel.downloadFromYoutube(item.name, item.uploaderName ?: "Unknown") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(AppIcons.Download, null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AudioTrimmerView(viewModel: MusicViewModel) {
    val song = viewModel.editingSong.value ?: return
    var startValue by remember { mutableStateOf(0f) }
    var endValue by remember { mutableStateOf(song.duration.toFloat()) }
    val isTrimming by viewModel.isTrimming.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Audio Trimmer", style = MaterialTheme.typography.headlineMedium, color = Color.Yellow, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(song.title, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
        
        Spacer(Modifier.height(48.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WaveformVisualizer(viewModel = viewModel)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Start: ${formatTime(startValue.toLong())}", color = Color.Yellow)
            Text("End: ${formatTime(endValue.toLong())}", color = Color.Yellow)
        }
        
        RangeSlider(
            value = startValue..endValue,
            onValueChange = { range ->
                startValue = range.start
                endValue = range.endInclusive
            },
            valueRange = 0f..song.duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.Yellow,
                activeTrackColor = Color.Yellow,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
        
        Text("Duration: ${formatTime((endValue - startValue).toLong())}", color = Color.White.copy(alpha = 0.5f))
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = { viewModel.trimAudio(song, startValue.toLong(), endValue.toLong()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTrimming && (endValue - startValue) > 1000,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black)
        ) {
            if (isTrimming) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
            else Text("Trim & Save to Music Folder")
        }
    }
}

@Composable
fun SmartPlaylistBuilderView(viewModel: MusicViewModel) {
    var name by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var minYear by remember { mutableStateOf("") }
    var maxYear by remember { mutableStateOf("") }
    var minPlayCount by remember { mutableStateOf("") }
    var onlyFavorites by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf("50") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Smart Playlist Builder", style = MaterialTheme.typography.headlineMedium, color = Color.Yellow, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        
        Text("Rules", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
        
        OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre (Optional)") }, modifier = Modifier.fillMaxWidth())
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = minYear, onValueChange = { minYear = it }, label = { Text("Min Year") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = maxYear, onValueChange = { maxYear = it }, label = { Text("Max Year") }, modifier = Modifier.weight(1f))
        }

        OutlinedTextField(value = minPlayCount, onValueChange = { minPlayCount = it }, label = { Text("Min Play Count") }, modifier = Modifier.fillMaxWidth())
        
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = onlyFavorites, onCheckedChange = { onlyFavorites = it }, colors = CheckboxDefaults.colors(checkedColor = Color.Yellow))
            Text("Only Favorites", color = Color.White)
        }
        
        OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("Max Songs") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val rules = mutableMapOf<String, Any>()
                if (genre.isNotEmpty()) rules["genre"] = genre
                minYear.toIntOrNull()?.let { rules["minYear"] = it }
                maxYear.toIntOrNull()?.let { rules["maxYear"] = it }
                minPlayCount.toIntOrNull()?.let { rules["minPlayCount"] = it }
                if (onlyFavorites) rules["isFavorite"] = true
                limit.toIntOrNull()?.let { rules["limit"] = it }
                
                val rulesJson = com.google.gson.Gson().toJson(rules)
                viewModel.createSmartPlaylist(name, rulesJson)
                viewModel.setView(MusicViewModel.View.PLAYLISTS)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black)
        ) {
            Text("Create Smart Playlist")
        }
    }
}

@Composable
fun MusicQuizView(viewModel: MusicViewModel) {
    val quizState by viewModel.quizState.collectAsState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    LaunchedEffect(Unit) {
        if (quizState.currentSong == null) {
            viewModel.startNewQuiz()
        }
    }

    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Music Quiz", style = MaterialTheme.typography.headlineMedium, color = Color.Yellow, fontWeight = FontWeight.Bold)
                Text("Score: ${quizState.score} / ${quizState.attempts}", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
                
                Spacer(Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(2.dp, Color.Yellow.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QuestionMark, null, modifier = Modifier.size(60.dp), tint = Color.Yellow)
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isPlaying by viewModel.isPlaying
                    IconButton(onClick = { viewModel.togglePlayPause() }) {
                        Icon(if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(48.dp), tint = Color.Yellow)
                    }
                    if (quizState.lastCorrect != false) {
                        TextButton(onClick = { viewModel.startNewQuiz() }) {
                            Text("Skip / Next Song", color = Color.Yellow)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1.5f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Which song is playing?", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(16.dp))
                
                quizState.options.forEach { option ->
                    val isSelected = quizState.lastCorrect != null && option == quizState.currentSong?.title
                    Button(
                        onClick = { if (quizState.lastCorrect == null) viewModel.submitQuizAnswer(option) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color.Green.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(option, textAlign = TextAlign.Center)
                    }
                }
                
                if (quizState.lastCorrect == false) {
                    Button(onClick = { viewModel.resetQuiz() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.6f))) {
                        Text("Retry New Song")
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Music Quiz", style = MaterialTheme.typography.headlineLarge, color = Color.Yellow, fontWeight = FontWeight.Bold)
            Text("Score: ${quizState.score} / ${quizState.attempts}", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
            
            Spacer(Modifier.height(48.dp))
            
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(2.dp, Color.Yellow.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.QuestionMark, null, modifier = Modifier.size(80.dp), tint = Color.Yellow)
            }
            
            Spacer(Modifier.height(48.dp))
            
            Text("Which song is playing?", style = MaterialTheme.typography.titleLarge, color = Color.White)
            
            Spacer(Modifier.height(24.dp))
            
            quizState.options.forEach { option ->
                val isSelected = quizState.lastCorrect != null && option == quizState.currentSong?.title
                
                Button(
                    onClick = { if (quizState.lastCorrect == null) viewModel.submitQuizAnswer(option) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isSelected -> Color.Green.copy(alpha = 0.6f)
                            else -> Color.White.copy(alpha = 0.1f)
                        },
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(option, textAlign = TextAlign.Center)
                }
            }
            
            if (quizState.lastCorrect == false) {
                Text("Wrong! Try again.", color = Color.Red, modifier = Modifier.padding(top = 16.dp))
                Button(
                    onClick = { viewModel.resetQuiz() },
                    modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.6f))
                ) {
                    Text("Retry New Song")
                }
            } else if (quizState.lastCorrect == true) {
                Text("Correct!", color = Color.Green, modifier = Modifier.padding(top = 16.dp))
            }
            
            Spacer(Modifier.height(48.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isPlaying by viewModel.isPlaying
                IconButton(onClick = { viewModel.togglePlayPause() }) {
                    Icon(
                        if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Yellow
                    )
                }
                
                if (quizState.lastCorrect != false) {
                    Spacer(Modifier.width(24.dp))
                    TextButton(onClick = { viewModel.startNewQuiz() }) {
                        Text("Skip / Next Song", color = Color.Yellow)
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun YouTubeVideoPlayer(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val playbackPosition by viewModel.playbackPosition
    val currentSong by viewModel.currentSong
    val isPlaying by viewModel.isPlaying
    val isVideoMode by viewModel.isVideoMode
    
    if (currentSong == null || !isVideoMode) return

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = viewModel.player
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = currentSong!!.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong!!.artist,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Slider(
                        value = playbackPosition.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..(currentSong!!.duration.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(thumbColor = Color.Yellow, activeTrackColor = Color.Yellow)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(playbackPosition), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Text(formatTime(currentSong!!.duration), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.skipPrevious() }) {
                            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        
                        FloatingActionButton(
                            onClick = { viewModel.togglePlayPause() },
                            containerColor = Color.Yellow,
                            contentColor = Color.Black,
                            shape = CircleShape
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(36.dp))
                        }
                        
                        IconButton(onClick = { viewModel.skipNext() }) {
                            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagEditorView(viewModel: MusicViewModel) {
    val song = viewModel.editingSong.value ?: return
    
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var albumArtist by remember { mutableStateOf(song.albumArtist ?: "") }
    var genre by remember { mutableStateOf(song.genre ?: "") }
    var year by remember { mutableStateOf(song.year.toString()) }
    var trackNumber by remember { mutableStateOf(song.trackNumber.toString()) }
    var discNumber by remember { mutableStateOf(song.discNumber.toString()) }
    var composer by remember { mutableStateOf(song.composer ?: "") }
    var comment by remember { mutableStateOf(song.comment ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Tag Editor", style = MaterialTheme.typography.headlineMedium, color = Color.Yellow, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = albumArtist, onValueChange = { albumArtist = it }, label = { Text("Album Artist") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre") }, modifier = Modifier.fillMaxWidth())
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = trackNumber, onValueChange = { trackNumber = it }, label = { Text("Track #") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = discNumber, onValueChange = { discNumber = it }, label = { Text("Disc #") }, modifier = Modifier.weight(1f))
        }
        
        OutlinedTextField(value = composer, onValueChange = { composer = it }, label = { Text("Composer") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.saveFullMetadata(
                    song = song,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist.ifEmpty { null },
                    genre = genre.ifEmpty { null },
                    year = year.toIntOrNull() ?: 0,
                    trackNumber = trackNumber.toIntOrNull() ?: 0,
                    discNumber = discNumber.toIntOrNull() ?: 0,
                    composer = composer.ifEmpty { null },
                    comment = comment.ifEmpty { null }
                )
                viewModel.setView(MusicViewModel.View.SONGS)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black)
        ) {
            Text("Save Tags")
        }
        
        TextButton(
            onClick = { viewModel.setView(MusicViewModel.View.SONGS) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun SongsHeader(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    viewModel: MusicViewModel,
    title: String? = null,
    subtitle: String? = null,
    imageUrl: Any? = null,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.filteredSongs.collectAsState()
    val currentSong by viewModel.currentSong
    
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = (songs.indexOfFirst { it.id == currentSong?.id }).coerceAtLeast(0),
        pageCount = { songs.size }
    )

    LaunchedEffect(currentSong) {
        val index = songs.indexOfFirst { it.id == currentSong?.id }
        if (index != -1 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val goldBrush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFD4AF37), Color(0xFFFFD700).copy(alpha = 0.6f))
            )
            
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(goldBrush, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(viewModel.translate("Play All"), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Button(
                onClick = onShuffle,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = goldBrush, shape = RoundedCornerShape(24.dp), alpha = 0.2f)
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Shuffle, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(viewModel.translate("Shuffle"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentPadding = PaddingValues(horizontal = 120.dp),
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            val song = songs[page]
            val isSelected = song.id == currentSong?.id
            
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.2f else 0.85f,
                animationSpec = tween(300),
                label = "scale"
            )

            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable { viewModel.playSong(song, songs) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val albumArtUri = if (song.hasEmbeddedArt) {
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                } else {
                    song.albumImageUrl?.let { Uri.parse(it) }
                }

                AsyncImage(
                    model = albumArtUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
                            else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        ),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(AppIcons.Album),
                    error = rememberVectorPainter(AppIcons.Album)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    song.title, 
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f), 
                    fontSize = 12.sp, 
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(100.dp)
                )
                Text(
                    song.artist, 
                    color = Color.White.copy(alpha = 0.5f), 
                    fontSize = 10.sp, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistListItem(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: ImageVector = AppIcons.PlaylistPlay
) {
    ListItem(
        headlineContent = { Text(playlist.name) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    )
}


@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    viewModel: MusicViewModel,
    onPlaylistSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(viewModel.translate("Add to Playlist")) },
        text = {
            LazyColumn {
                items(playlists) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        modifier = Modifier.clickable { onPlaylistSelected(playlist.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(viewModel.translate("Cancel")) }
        }
    )
}


@Composable
fun ArtistImageDialog(
    artist: ArtistInfo,
    onPickFromGallery: () -> Unit,
    onSetUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    isOnline: Boolean = true
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Artist Image: ${artist.name}") },
        text = {
            Column {
                Button(onClick = onPickFromGallery, modifier = Modifier.fillMaxWidth()) {
                    Text("Pick from Gallery")
                }
                if (isOnline) {
                    Text("OR", modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Image URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Online Features disabled. URL option unavailable.", modifier = Modifier.padding(top = 16.dp).alpha(0.5f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            if (isOnline) {
                TextButton(onClick = { if (url.isNotBlank()) onSetUrl(url) }) { Text("Save URL") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
fun SongOptionsDialog(
    song: Song,
    viewModel: MusicViewModel,
    onFixMetadata: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSetArtwork: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleNotPodcast: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onIdentifySong: () -> Unit,
    onSetGenre: () -> Unit,
    onDelete: () -> Unit,
    onStartRadio: () -> Unit,
    onDismiss: () -> Unit,
    isOnline: Boolean = true
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ListItem(
                    headlineContent = { Text(viewModel.translate("Play Next")) },
                    leadingContent = { Icon(AppIcons.PlaylistPlay, null) },
                    modifier = Modifier.clickable { onPlayNext() }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate("Add to Queue")) },
                    leadingContent = { Icon(AppIcons.PlaylistPlay, null) },
                    modifier = Modifier.clickable { onAddToQueue() }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate("Start YouTube Radio"), color = if (isOnline) Color.Unspecified else Color.Gray) },
                    leadingContent = { Icon(Icons.Default.Radio, null, tint = if (isOnline) LocalContentColor.current else Color.Gray) },
                    modifier = Modifier.clickable { 
                        if (isOnline) onStartRadio()
                        else android.widget.Toast.makeText(context, viewModel.translate("Online Features disabled. Go to Settings to enable."), android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text(viewModel.translate("Go to Artist")) },
                    supportingContent = { Text(song.artist) },
                    leadingContent = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.clickable { onGoToArtist() }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate("Go to Album")) },
                    supportingContent = { Text(song.album) },
                    leadingContent = { Icon(AppIcons.Album, null) },
                    modifier = Modifier.clickable { onGoToAlbum() }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text(viewModel.translate(if (song.manualNotPodcast) "Set as Podcast" else "Set as Song")) },
                    leadingContent = { Icon(if (song.manualNotPodcast) AppIcons.Podcasts else AppIcons.MusicNote, null) },
                    modifier = Modifier.clickable { onToggleNotPodcast() }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate("Fix Metadata / Artwork")) },
                    leadingContent = { Icon(AppIcons.Edit, null) },
                    modifier = Modifier.clickable { onFixMetadata() }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate("Full Tag Editor")) },
                    leadingContent = { Icon(AppIcons.EditNote, null) },
                    modifier = Modifier.clickable { 
                        viewModel.setEditingSong(song)
                        onDismiss()
                    }
                )

                ListItem(
                    headlineContent = { Text(viewModel.translate("Trim Audio / Ringtone")) },
                    leadingContent = { Icon(Icons.Default.ContentCut, null) },
                    modifier = Modifier.clickable { 
                        viewModel.startTrimmer(song)
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate("Identify Song (AcoustID)"), color = if (isOnline) Color.Unspecified else Color.Gray) },
                    leadingContent = { Icon(AppIcons.AutoFixHigh, null, tint = if (isOnline) LocalContentColor.current else Color.Gray) },
                    modifier = Modifier.clickable { 
                        if (isOnline) onIdentifySong() 
                        else android.widget.Toast.makeText(context, viewModel.translate("Online Features disabled. Go to Settings to enable."), android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate(if (isOnline) "Set Artwork from Local / URL" else "Set Artwork from Local")) },
                    leadingContent = { Icon(AppIcons.Image, null) },
                    modifier = Modifier.clickable { onSetArtwork() }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate("Set Genre")) },
                    leadingContent = { Icon(AppIcons.Label, null) },
                    modifier = Modifier.clickable { onSetGenre() }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate("Add to Playlist")) },
                    leadingContent = { Icon(AppIcons.PlaylistPlay, null) },
                    modifier = Modifier.clickable { onAddToPlaylist() }
                )
                ListItem(
                    headlineContent = { Text(viewModel.translate("Hide from Library")) },
                    leadingContent = { Icon(Icons.Default.VisibilityOff, null) },
                    modifier = Modifier.clickable { 
                        viewModel.hideSong(song)
                        onDismiss()
                    }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text(viewModel.translate("Delete from Device"), color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onDelete() }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(viewModel.translate("Cancel")) }
        }
    )
}

@Composable
fun GenreEditDialog(
    song: Song,
    viewModel: MusicViewModel,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var genre by remember { mutableStateOf(song.genre ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(viewModel.translate("Set Genre")) },
        text = {
            OutlinedTextField(
                value = genre,
                onValueChange = { genre = it },
                label = { Text(viewModel.translate("Genre")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(genre) }) { Text(viewModel.translate("Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(viewModel.translate("Cancel")) }
        }
    )
}


@Composable
fun SongArtworkDialog(
    song: Song,
    onPickFromGallery: () -> Unit,
    onSetUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    isOnline: Boolean = true
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Song Artwork: ${song.title}") },
        text = {
            Column {
                Button(onClick = onPickFromGallery, modifier = Modifier.fillMaxWidth()) {
                    Text("Pick from Gallery")
                }
                if (isOnline) {
                    Text("OR", modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Image URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Online Features disabled. URL option unavailable.", modifier = Modifier.padding(top = 16.dp).alpha(0.5f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            if (isOnline) {
                TextButton(onClick = { if (url.isNotBlank()) onSetUrl(url) }) { Text("Save URL") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
fun AlbumArtworkDialog(
    album: AlbumInfo,
    onPickFromGallery: () -> Unit,
    onSetUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    isOnline: Boolean = true
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Album Artwork: ${album.title}") },
        text = {
            Column {
                Button(onClick = onPickFromGallery, modifier = Modifier.fillMaxWidth()) {
                    Text("Pick from Gallery")
                }
                if (isOnline) {
                    Text("OR", modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Image URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Online Features disabled. URL option unavailable.", modifier = Modifier.padding(top = 16.dp).alpha(0.5f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            if (isOnline) {
                TextButton(onClick = { if (url.isNotBlank()) onSetUrl(url) }) { Text("Save URL") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

data class ArtistInfo(val name: String, val imageUrl: Any?, val songs: List<Song>)
data class AlbumInfo(val title: String, val artist: String, val imageUrl: Any?, val songs: List<Song>)
data class GenreInfo(val name: String, val songs: List<Song>)

@Composable
fun InsightsView(viewModel: MusicViewModel) {
    val songs by viewModel.songs.collectAsState()
    
    val totalTime = remember(songs) { songs.sumOf { it.totalPlayTimeMs } }
    val topArtist = remember(songs) { songs.groupBy { it.artist }.maxByOrNull { it.value.sumOf { s -> s.playCount } }?.key ?: "N/A" }
    val topGenre = remember(songs) { songs.filter { it.genre != null }.groupBy { it.genre!! }.maxByOrNull { it.value.sumOf { s -> s.playCount } }?.key ?: "N/A" }
    val totalPlays = remember(songs) { songs.sumOf { it.playCount } }

    val topSongs = remember(songs) {
        songs.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(5)
    }

    val forgottenGem = remember(songs) {
        songs.filter { it.isFavorite && it.lastPlayed > 0 && (System.currentTimeMillis() - it.lastPlayed > 14L * 24 * 60 * 60 * 1000) }
            .minByOrNull { it.lastPlayed }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(viewModel.translate("Library Insights"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.Yellow)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(viewModel.translate("Total Listening Time"), style = MaterialTheme.typography.titleSmall, color = Color.Yellow.copy(alpha = 0.7f))
                Text(formatDuration(totalTime), style = MaterialTheme.typography.headlineMedium, color = Color.Yellow)
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(viewModel.translate("Top Artist"), style = MaterialTheme.typography.titleSmall, color = Color.Yellow.copy(alpha = 0.7f))
                    Text(topArtist, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Yellow)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(viewModel.translate("Top Genre"), style = MaterialTheme.typography.titleSmall, color = Color.Yellow.copy(alpha = 0.7f))
                    Text(topGenre, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Yellow)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(viewModel.translate("Total Plays"), style = MaterialTheme.typography.titleSmall, color = Color.Yellow.copy(alpha = 0.7f))
                Text(totalPlays.toString(), style = MaterialTheme.typography.headlineMedium, color = Color.Yellow)
            }
        }

        if (forgottenGem != null) {
            Spacer(Modifier.height(24.dp))
            Text(viewModel.translate("Forgotten Gem"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.playSong(forgottenGem) },
                colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.1f))
            ) {
                ListItem(
                    headlineContent = { Text(forgottenGem.title, color = Color.Yellow) },
                    supportingContent = { Text(viewModel.translate("Last played") + " ${formatDate(forgottenGem.lastPlayed)}", color = Color.Yellow.copy(alpha = 0.7f)) },
                    leadingContent = { Icon(AppIcons.History, null, tint = Color.Yellow) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        if (topSongs.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(viewModel.translate("Top 5 Songs"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            topSongs.forEachIndexed { index, song ->
                ListItem(
                    headlineContent = { Text(song.title, color = Color.White) },
                    supportingContent = { Text(song.artist, color = Color.White.copy(alpha = 0.7f)) },
                    trailingContent = { Text("${song.playCount}", color = Color.Yellow) },
                    leadingContent = { Text("${index + 1}", color = Color.Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp)) },
                    modifier = Modifier.clickable { viewModel.playSong(song) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    return format.format(date)
}

fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = (ms / (1000 * 60 * 60)) % 24
    val days = ms / (1000 * 60 * 60 * 24)
    
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        else -> "${minutes}m ${seconds}s"
    }
}

@Composable
fun FolderBrowserView(viewModel: MusicViewModel) {
    val songs by viewModel.songs.collectAsState()
    var currentPath by remember { mutableStateOf<String?>(null) }

    val folders = remember(songs, currentPath) {
        if (currentPath == null) {
            songs.map { File(it.path).parent ?: "Unknown" }
                .distinct()
                .sorted()
        } else {
            emptyList()
        }
    }

    val songsInFolder = remember(songs, currentPath) {
        if (currentPath != null) {
            songs.filter { File(it.path).parent == currentPath }
                .sortedBy { it.title.lowercase() }
        } else {
            emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (currentPath != null) {
            ListItem(
                headlineContent = { Text(".. (Back)") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                modifier = Modifier.clickable { currentPath = null }
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (currentPath == null) {
                items(folders) { folderPath ->
                    ListItem(
                        headlineContent = { Text(folderPath.substringAfterLast('/')) },
                        supportingContent = { Text(folderPath) },
                        leadingContent = { Icon(AppIcons.Folder, null) },
                        modifier = Modifier.clickable { currentPath = folderPath }
                    )
                }
            } else {
                items(songsInFolder) { song ->
                    SongItem(
                        song = song,
                        isSelected = song.id == viewModel.currentSong.value?.id,
                        onClick = { viewModel.playSong(song) },
                        onLongClick = {},
                        onArtworkClick = { viewModel.toggleSongSelection(song.id) },
                        onFavoriteToggle = { viewModel.toggleFavorite(song) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistGridItem(
    artist: ArtistInfo,
    gridColumns: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val imageSize = when(gridColumns) {
        2 -> 140.dp
        3 -> 100.dp
        4 -> 80.dp
        else -> 60.dp
    }
    val fontSize = if (gridColumns > 3) 12.sp else 14.sp

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            placeholder = rememberVectorPainter(Icons.Default.Person),
            error = rememberVectorPainter(Icons.Default.Person),
            fallback = rememberVectorPainter(Icons.Default.Person),
            modifier = Modifier
                .size(imageSize)
                .clip(RoundedCornerShape(imageSize / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = fontSize,
            color = Color.Yellow
        )
        Text(
            text = "${artist.songs.size} songs",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Yellow.copy(alpha = 0.7f),
            fontSize = fontSize * 0.8f
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumGridItem(
    album: AlbumInfo,
    gridColumns: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val imageSize = when(gridColumns) {
        2 -> 140.dp
        3 -> 100.dp
        4 -> 80.dp
        else -> 60.dp
    }
    val fontSize = if (gridColumns > 3) 12.sp else 14.sp

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = album.imageUrl,
            contentDescription = album.title,
            placeholder = rememberVectorPainter(AppIcons.Album),
            error = rememberVectorPainter(AppIcons.Album),
            fallback = rememberVectorPainter(AppIcons.Album),
            modifier = Modifier
                .size(imageSize)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = fontSize,
            color = Color.Yellow
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Yellow.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = fontSize * 0.8f
        )
    }
}

@Composable
fun SyncedLyricsView(
    lyrics: List<MusicViewModel.LyricLine>,
    currentPosition: Long,
    onLineClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    isTranslationEnabled: Boolean = false,
    onToggleTranslation: () -> Unit = {}
) {
    Box(modifier = modifier) {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        
        val activeIndex = remember(currentPosition, lyrics) {
            val index = lyrics.indexOfLast { it.timeMs <= currentPosition }
            index.coerceAtLeast(0)
        }

        var isUserInteracting by remember { mutableStateOf(false) }

        LaunchedEffect(activeIndex) {
            if (lyrics.isNotEmpty() && !isUserInteracting) {
                listState.animateScrollToItem(activeIndex, scrollOffset = -350)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { isUserInteracting = true },
                    onDragEnd = { 
                        scope.launch {
                            delay(3000)
                            isUserInteracting = false
                        }
                    },
                    onDragCancel = { isUserInteracting = false },
                    onVerticalDrag = { _, _ -> }
                )
            },
            contentPadding = PaddingValues(vertical = 200.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(lyrics.size) { index ->
                val line = lyrics[index]
                val isActive = index == activeIndex
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLineClick(line.timeMs) }
                        .padding(vertical = 12.dp, horizontal = 24.dp)
                        .animateContentSize()
                ) {
                    Text(
                        text = line.text,
                        fontSize = if (isActive) 24.sp else 18.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    if (isTranslationEnabled && line.translatedText != null) {
                        Text(
                            text = line.translatedText!!,
                            fontSize = if (isActive) 18.sp else 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isActive) Color.Yellow.copy(alpha = 0.8f) else Color.Yellow.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleTranslation) {
                Icon(
                    AppIcons.Translate, 
                    contentDescription = "Translate", 
                    tint = if (isTranslationEnabled) Color.Yellow else Color.White
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close Lyrics", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    isSelected: Boolean,
    isMultiSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onArtworkClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        headlineContent = { 
            Text(
                song.title, 
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        supportingContent = { 
            val subtitle = "${song.artist} • ${song.album}"
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.6f)) 
        },
        leadingContent = {
            val albumArtUri = if (song.hasEmbeddedArt) {
                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
            } else {
                song.albumImageUrl?.let { Uri.parse(it) }
            }
            AsyncImage(
                model = albumArtUri,
                contentDescription = null,
                placeholder = rememberVectorPainter(AppIcons.MusicNote),
                error = rememberVectorPainter(AppIcons.MusicNote),
                fallback = rememberVectorPainter(AppIcons.MusicNote),
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (song.isFavorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = formatTime(song.duration),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun FixMetadataDialog(song: Song, viewModel: MusicViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching
    val isOnline by viewModel.isOnlineMode.collectAsState()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix Metadata") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") })
                Button(
                    onClick = { 
                        if (isOnline) {
                            viewModel.searchMetadata(title, artist) 
                        } else {
                            android.widget.Toast.makeText(context, "Online Features disabled. Go to Settings to enable.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                    enabled = !isSearching,
                    colors = if (isOnline) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    if (isSearching) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Search Online")
                }
                
                if (searchResults.isNotEmpty()) {
                    Text("Search Results:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(searchResults) { result ->
                            ListItem(
                                headlineContent = { Text(result.title) },
                                supportingContent = { Text("${result.artist} - ${result.album}") },
                                leadingContent = {
                                    AsyncImage(
                                        model = result.albumImageUrl,
                                        contentDescription = null,
                                        placeholder = rememberVectorPainter(AppIcons.Album),
                                        error = rememberVectorPainter(AppIcons.Album),
                                        fallback = rememberVectorPainter(AppIcons.Album),
                                        modifier = Modifier.size(40.dp)
                                    )
                                },
                                modifier = Modifier.clickable {
                                    viewModel.applyManualMetadata(song, result)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.saveManualMetadata(song, title, artist)
                onDismiss()
            }) { Text("Save Local") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlaybackControls(
    song: Song,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    viewModel: MusicViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val playbackPosition by viewModel.playbackPosition
    val glassEffectEnabled by viewModel.glassEffectEnabled.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (glassEffectEnabled) Color.Black.copy(alpha = 0.85f) else Color.DarkGray)
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .swipeToSkip(
                onSwipeLeft = { viewModel.skipNext() },
                onSwipeRight = { viewModel.skipPrevious() }
            )
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val albumArtUri = if (song.hasEmbeddedArt) {
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                } else {
                    song.albumImageUrl?.let { Uri.parse(it) }
                }
                with(sharedTransitionScope) {
                    AsyncImage(
                        model = albumArtUri,
                        contentDescription = null,
                        placeholder = rememberVectorPainter(AppIcons.MusicNote),
                        error = rememberVectorPainter(AppIcons.MusicNote),
                        fallback = rememberVectorPainter(AppIcons.MusicNote),
                        modifier = Modifier
                            .size(52.dp)
                            .sharedElement(
                                rememberSharedContentState(key = "artwork"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
                IconButton(onClick = { viewModel.skipPrevious() }) {
                    Icon(AppIcons.SkipPrevious, contentDescription = null, tint = Color.White)
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { viewModel.skipNext() }) {
                    Icon(AppIcons.SkipNext, contentDescription = null, tint = Color.White)
                }
            }
            
            if (song.duration > 0) {
                LinearProgressIndicator(
                    progress = { (playbackPosition.toFloat() / song.duration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color(0xFFFFD700),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
fun Visualizer(viewModel: MusicViewModel) {
    val barCount = 32
    val magnitudes by viewModel.visualizerData
    val isPlaying by viewModel.isPlaying
    val dominantColor by viewModel.dominantColor
    
    val baseColor = if (dominantColor != 0) Color(dominantColor) else Color.Yellow
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            if (index == barCount / 2) {
                Spacer(Modifier.width(12.dp))
            }
            
            val mag = if (index < magnitudes.size) magnitudes[index] else 0f
            val targetHeight = if (isPlaying) (mag * 1.5f).coerceIn(0.1f, 1.5f) else 0.1f
            
            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight,
                animationSpec = tween(100, easing = FastOutSlowInEasing),
                label = "barHeight"
            )
            
            val alpha = (0.4f + (index.toFloat() / barCount) * 0.6f).coerceIn(0.4f, 1.0f)
            val barColor = baseColor.copy(alpha = alpha)

            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(4.dp)
                    .height((30f * animatedHeight).dp)
                    .background(barColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FullPlayerView(
    song: Song,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    onTogglePlayPause: () -> Unit,
    onDismiss: () -> Unit,
    onShowQueue: () -> Unit,
    onShowArtistBio: (String) -> Unit,
    onShowFullResolutionArt: (Any) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val playbackPosition by viewModel.playbackPosition
    val isVideoMode by viewModel.isVideoMode
    val showLyrics by viewModel.showLyrics
    val lyrics by viewModel.currentLyrics
    val repeatMode by viewModel.repeatMode
    val shuffleMode by viewModel.shuffleMode
    val isOnline by viewModel.isOnlineMode.collectAsState()
    val glassEffectEnabled by viewModel.glassEffectEnabled.collectAsState()
    
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (showLyrics && lyrics.isNotEmpty()) {
                val isTranslationEnabled by viewModel.isTranslationEnabled.collectAsState()
                SyncedLyricsView(
                    lyrics = lyrics,
                    currentPosition = playbackPosition,
                    onLineClick = { timeMs -> viewModel.seekTo(timeMs) },
                    modifier = Modifier.fillMaxSize(),
                    onClose = { viewModel.toggleLyrics() },
                    isTranslationEnabled = isTranslationEnabled,
                    onToggleTranslation = { viewModel.setTranslationEnabled(!isTranslationEnabled) }
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                ) {
                    Spacer(Modifier.height(20.dp))
                    
                    val albumArtUri = if (song.hasEmbeddedArt) {
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                    } else {
                        song.albumImageUrl?.let { Uri.parse(it) }
                    }

                    with(sharedTransitionScope) {
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .padding(12.dp)
                                .graphicsLayer {
                                    shadowElevation = 40f
                                    shape = RoundedCornerShape(16.dp)
                                    clip = true
                                }
                                .border(
                                    BorderStroke(
                                        3.dp, 
                                        Brush.linearGradient(
                                            listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.5f))
                                        )
                                    ), 
                                    RoundedCornerShape(16.dp)
                                )
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent, Color.Black.copy(alpha = 0.2f))
                                    )
                                )
                                .combinedClickable(
                                    onClick = { 
                                        if (isOnline) onShowArtistBio(song.artist) 
                                        else android.widget.Toast.makeText(context, "Online Features disabled.", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onDoubleClick = { onShowFullResolutionArt(albumArtUri ?: "") }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideoMode) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = viewModel.player
                                            useController = false
                                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                AsyncImage(
                                    model = albumArtUri,
                                    contentDescription = null,
                                    placeholder = rememberVectorPainter(AppIcons.MusicNote),
                                    error = rememberVectorPainter(AppIcons.MusicNote),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedElement(
                                            rememberSharedContentState(key = "artwork"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.15f))
                                        )
                                    )
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = song.artist,
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = song.title,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black.copy(alpha = 0.3f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.offset(y = 2.dp)
                        )
                        Text(
                            text = song.title,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .basicMarquee()
                                .graphicsLayer { 
                                    alpha = 0.95f
                                    shadowElevation = 8f
                                }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.BottomCenter) {
            Visualizer(viewModel = viewModel)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(
                    if (glassEffectEnabled) Color.Black.copy(alpha = 0.85f)
                    else Color(0xFF121212)
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    WaveformVisualizer(viewModel = viewModel)
                }
                
                Spacer(Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(playbackPosition), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    Text(formatTime(song.duration), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
                
                Slider(
                    value = playbackPosition.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..song.duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.3f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                        Icon(AppIcons.Shuffle, null, tint = if (shuffleMode) Color.White else Color.White.copy(alpha = 0.3f))
                    }
                    IconButton(onClick = { viewModel.skipPrevious() }) {
                        Icon(AppIcons.SkipPrevious, null, modifier = Modifier.size(36.dp), tint = Color.White)
                    }
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                                )
                            )
                            .border(2.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable { onTogglePlayPause() }
                    ) {
                        Icon(
                            if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { viewModel.skipNext() }) {
                        Icon(AppIcons.SkipNext, null, modifier = Modifier.size(36.dp), tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                        Icon(
                            when(repeatMode) {
                                Player.REPEAT_MODE_ONE -> AppIcons.RepeatOne
                                Player.REPEAT_MODE_ALL -> AppIcons.Repeat
                                else -> AppIcons.Repeat
                            },
                            null,
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showLyricsEditor by remember { mutableStateOf(false) }
                    if (showLyricsEditor) {
                        LyricsEditorDialog(
                            song = song,
                            viewModel = viewModel,
                            onDismiss = { showLyricsEditor = false }
                        )
                    }

                    IconButton(onClick = { viewModel.toggleLyrics() }) {
                        Icon(
                            AppIcons.Lyrics, 
                            null, 
                            tint = if (showLyrics) Color.White else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { showLyricsEditor = true }) {
                        Icon(AppIcons.EditNote, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(26.dp))
                    }
                    IconButton(onClick = { 
                        val albumArtUri = if (song.hasEmbeddedArt) {
                            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                        } else {
                            song.albumImageUrl?.let { Uri.parse(it) }
                        }

                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, "Listening to ${song.title} by ${song.artist} on Music Player!")
                            if (albumArtUri != null) {
                                putExtra(android.content.Intent.EXTRA_STREAM, albumArtUri)
                                type = "image/*"
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } else {
                                type = "text/plain"
                            }
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Song"))
                    }) {
                        Icon(Icons.Default.Share, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                    }
                    
                    if (isOnline) {
                        IconButton(onClick = { viewModel.playMusicVideo(song) }) {
                            Icon(
                                Icons.Default.Movie, 
                                null, 
                                tint = if (isVideoMode) Color.Yellow else Color.White.copy(alpha = 0.5f), 
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onShowQueue() },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White.copy(alpha = 0.4f))
                    Text("UP NEXT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun WaveformVisualizer(viewModel: MusicViewModel) {
    val magnitudes by viewModel.visualizerData
    val playbackPosition by viewModel.playbackPosition
    val currentSong by viewModel.currentSong
    val progress = if (currentSong?.duration ?: 0 > 0) playbackPosition.toFloat() / currentSong!!.duration else 0f

    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).alpha(0.8f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val mirrored = magnitudes.reversed() + magnitudes.toList()
        val doubleDensity = mirrored + mirrored
        
        doubleDensity.forEachIndexed { idx, mag ->
            val height = (mag * 25f).coerceIn(3f, 48f)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(height.dp)
                    .padding(horizontal = 0.2.dp)
                    .background(
                        color = if (idx.toFloat() / doubleDensity.size < progress) Color.White else Color.White.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseClick: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search title, artist, album...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        }
    )
}

@Composable
fun LyricsEditorDialog(
    song: Song,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val localFile = java.io.File(context.filesDir, "lyrics_${song.id}.lrc")
        if (localFile.exists()) {
            rawText = localFile.readText()
        } else {
            val isSynced = viewModel.currentLyrics.value.any { it.timeMs > 0 }
            rawText = viewModel.currentLyrics.value.joinToString("\n") { line ->
                if (isSynced) {
                    val minutes = (line.timeMs / 1000) / 60
                    val seconds = (line.timeMs / 1000) % 60
                    val ms = (line.timeMs % 1000) / 10
                    String.format("[%02d:%02d.%02d] %s", minutes, seconds, ms, line.text)
                } else {
                    line.text
                }
            }
        }
    }

    val playbackPosition by viewModel.playbackPosition
    val isPlaying by viewModel.isPlaying

    if (!isSyncing) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit / Sync Lyrics") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Paste lyrics below. If they already have timestamps (e.g. [01:23.45]), you can save directly.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        placeholder = { Text("Enter lyrics (with or without [mm:ss.xx] timestamps)...") }
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.saveSyncedLyrics(song, rawText)
                            onDismiss()
                        },
                        enabled = rawText.trim().isNotEmpty()
                    ) { Text("Save Directly") }
                    Button(
                        onClick = { isSyncing = true },
                        enabled = rawText.trim().isNotEmpty()
                    ) { Text("Start Syncing") }
                }
            },
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.deleteSyncedLyrics(song)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Reset") }
                    
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        )
    } else {
        val lines = remember(rawText) {
            rawText.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
        }
        
        var currentIndex by remember { mutableIntStateOf(0) }
        val stampedLines = remember { mutableStateListOf<String>() }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Syncing: ${song.title}") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time: ${formatTime(playbackPosition)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(lines.size) { index ->
                                val isCurrent = index == currentIndex
                                val isPast = index < currentIndex
                                Text(
                                    text = lines[index],
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary 
                                            else if (isPast) Color.Gray 
                                            else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    style = if (isCurrent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { isSyncing = false }) { Text("Back") }
                    
                    Row {
                        Button(
                            onClick = {
                                if (currentIndex < lines.size) {
                                    val currentLine = lines[currentIndex]
                                    val minutes = (playbackPosition / 1000) / 60
                                    val seconds = (playbackPosition / 1000) % 60
                                    val ms = (playbackPosition % 1000) / 10
                                    val timestamp = String.format("[%02d:%02d.%02d]", minutes, seconds, ms)
                                    stampedLines.add("$timestamp $currentLine")
                                    currentIndex++
                                }
                            },
                            enabled = currentIndex < lines.size
                        ) {
                            Text("STAMP [TIME]")
                        }
                        
                        if (currentIndex >= lines.size || stampedLines.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val compiledLrc = stampedLines.joinToString("\n")
                                    viewModel.saveSyncedLyrics(song, compiledLrc)
                                    onDismiss()
                                }
                            ) {
                                Text("Save (${stampedLines.size}/${lines.size})")
                            }
                        }
                    }
                }
            }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun EqualizerView(viewModel: MusicViewModel) {
    val enabled by viewModel.eqEnabled.collectAsState()
    val bands by viewModel.eqBands.collectAsState()
    val bassBoost by viewModel.bassBoostStrength.collectAsState()
    val virtualizer by viewModel.virtualizerStrength.collectAsState()
    val limiterEnabled by viewModel.limiterEnabled.collectAsState()
    val reverbPreset by viewModel.reverbPreset.collectAsState()

    val frequencies = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    val reverbPresets = listOf("None", "Small Room", "Medium Room", "Large Room", "Medium Hall", "Large Hall", "Plate")

    val yellowSliderColors = SliderDefaults.colors(
        thumbColor = Color.Yellow,
        activeTrackColor = Color.Yellow,
        inactiveTrackColor = Color.Yellow.copy(alpha = 0.3f)
    )

    val presets = mapOf(
        "Flat" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        "Classical" to listOf(5, 4, 3, 2, -2, -2, 0, 3, 4, 5),
        "Dance" to listOf(6, 5, 2, 0, 0, -4, -6, -6, 0, 0),
        "Jazz" to listOf(4, 3, 1, 2, -2, -2, 0, 1, 3, 4),
        "Pop" to listOf(-2, -1, 0, 2, 5, 5, 2, 0, -1, -2),
        "Rock" to listOf(5, 4, 3, 1, -1, -2, 0, 3, 4, 5),
        "Bass Boost" to listOf(7, 6, 5, 4, 2, 0, 0, 0, 0, 0),
        "Treble Boost" to listOf(0, 0, 0, 0, 0, 2, 4, 5, 6, 7),
        "Vocal Boost" to listOf(-3, -2, 0, 3, 6, 6, 3, 0, -2, -3)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(viewModel.translate("Enable Master Effects"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
            Switch(
                checked = enabled, 
                onCheckedChange = { viewModel.setEqEnabled(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
            )
        }

        Spacer(Modifier.height(24.dp))
        
        Text(viewModel.translate("Equalizer Presets"), style = MaterialTheme.typography.titleSmall, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))
        
        var showPresetsMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { showPresetsMenu = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Yellow),
                border = BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.5f))
            ) {
                Text(viewModel.translate("Select Preset"))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(
                expanded = showPresetsMenu,
                onDismissRequest = { showPresetsMenu = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                presets.forEach { (name, levels) ->
                    DropdownMenuItem(
                        text = { Text(viewModel.translate(name)) },
                        onClick = {
                            viewModel.applyEqPreset(levels.withIndex().associate { it.index to it.value * 100 })
                            showPresetsMenu = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(viewModel.translate("Frequency Bands"), style = MaterialTheme.typography.titleSmall, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        frequencies.forEachIndexed { index, freq ->
            val level = bands[index] ?: 0
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = freq,
                    modifier = Modifier.width(50.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Yellow.copy(alpha = 0.8f)
                )
                Slider(
                    value = level.toFloat(),
                    onValueChange = { viewModel.setEqBand(index, it.toInt()) },
                    valueRange = -1500f..1500f,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    colors = yellowSliderColors
                )
                Text(
                    text = "${level / 100}dB",
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Yellow.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(viewModel.translate("Audio Enhancements"), style = MaterialTheme.typography.titleSmall, color = Color.Yellow)
        Spacer(Modifier.height(16.dp))

        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(viewModel.translate("Bass Boost"), style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
                Text("${bassBoost / 10}%", style = MaterialTheme.typography.bodySmall, color = Color.Yellow.copy(alpha = 0.7f))
            }
            Slider(
                value = bassBoost.toFloat(),
                onValueChange = { viewModel.setBassBoost(it.toInt()) },
                valueRange = 0f..1000f,
                enabled = enabled,
                colors = yellowSliderColors
            )
        }

        Spacer(Modifier.height(16.dp))

        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(viewModel.translate("Virtualizer"), style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
                Text("${virtualizer / 10}%", style = MaterialTheme.typography.bodySmall, color = Color.Yellow.copy(alpha = 0.7f))
            }
            Slider(
                value = virtualizer.toFloat(),
                onValueChange = { viewModel.setVirtualizer(it.toInt()) },
                valueRange = 0f..1000f,
                enabled = enabled,
                colors = yellowSliderColors
            )
        }
        
        Spacer(Modifier.height(16.dp))

        Text(viewModel.translate("Playback Control"), style = MaterialTheme.typography.titleSmall, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        val speed by viewModel.playbackSpeed.collectAsState()
        val pitch by viewModel.playbackPitch.collectAsState()

        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(viewModel.translate("Speed"), style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
                Text("%.2fx".format(speed), style = MaterialTheme.typography.bodySmall, color = Color.Yellow.copy(alpha = 0.7f))
            }
            Slider(
                value = speed,
                onValueChange = { viewModel.setPlaybackSpeed(it) },
                valueRange = 0.5f..2.0f,
                steps = 15,
                colors = yellowSliderColors
            )
        }

        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(viewModel.translate("Pitch"), style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
                Text("%.2fx".format(pitch), style = MaterialTheme.typography.bodySmall, color = Color.Yellow.copy(alpha = 0.7f))
            }
            Slider(
                value = pitch,
                onValueChange = { viewModel.setPlaybackPitch(it) },
                valueRange = 0.5f..2.0f,
                steps = 15,
                colors = yellowSliderColors
            )
        }

        Button(
            onClick = { viewModel.setPlaybackSpeed(1.0f); viewModel.setPlaybackPitch(1.0f) },
            modifier = Modifier.align(Alignment.End),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black)
        ) {
            Text(viewModel.translate("Reset Speed/Pitch"))
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(viewModel.translate("Loudness Limiter"), style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
                Text(viewModel.translate("Prevent audio clipping"), style = MaterialTheme.typography.bodySmall, color = Color.Yellow.copy(alpha = 0.5f))
            }
            Switch(
                checked = limiterEnabled, 
                onCheckedChange = { viewModel.setLimiterEnabled(it) }, 
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(viewModel.translate("Reverb Preset"), style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true }, 
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Yellow),
                    border = BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.5f))
                ) {
                    Text(viewModel.translate(reverbPresets.getOrElse(reverbPreset) { "None" }))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    reverbPresets.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(viewModel.translate(name)) },
                            onClick = {
                                viewModel.setReverbPreset(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun QueueView(viewModel: MusicViewModel) {
    val queue by viewModel.currentQueue.collectAsState()
    val currentIndex by viewModel.currentMediaItemIndex
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    val nextSongs = remember(queue, currentIndex) {
        if (currentIndex >= 0 && currentIndex < queue.size - 1) {
            queue.subList(currentIndex + 1, queue.size)
        } else {
            emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(viewModel.translate("Play Queue"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.Yellow)
            IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                val shuffleMode by viewModel.shuffleMode
                Icon(AppIcons.Shuffle, null, tint = if (shuffleMode) Color.Yellow else Color.White.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                )
                .border(
                    0.5.dp, 
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ), 
                    RoundedCornerShape(28.dp)
                )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (nextSongs.isNotEmpty()) {
                    itemsIndexed(nextSongs, key = { _, song -> "song_${song.id}" }) { indexInSublist, song ->
                        val actualIndex = currentIndex + 1 + indexInSublist
                        var dragOffset by remember { mutableStateOf(0f) }
                        val currentActualIndex by rememberUpdatedState(actualIndex)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDrag = { change, dragAmount ->
                                            change.consume() 
                                            dragOffset += dragAmount.y
                                            if (dragOffset < -80f && currentActualIndex > currentIndex + 1) {
                                                viewModel.moveQueueItem(currentActualIndex, currentActualIndex - 1)
                                                dragOffset = 0f
                                            } else if (dragOffset > 80f && currentActualIndex < queue.size - 1) {
                                                viewModel.moveQueueItem(currentActualIndex, currentActualIndex + 1)
                                                dragOffset = 0f
                                            }
                                        },
                                        onDragEnd = { dragOffset = 0f },
                                        onDragCancel = { dragOffset = 0f }
                                    )
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            ListItem(
                                headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.6f)) },
                                leadingContent = {
                                    val albumArtUri = if (song.hasEmbeddedArt) {
                                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                                    } else {
                                        song.albumImageUrl?.let { Uri.parse(it) }
                                    }
                                    AsyncImage(
                                        model = albumArtUri,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Menu, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(end = 8.dp))
                                        IconButton(onClick = { viewModel.removeFromQueue(actualIndex) }) {
                                            Icon(Icons.Default.Close, null, tint = Color.Red.copy(alpha = 0.7f))
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                } else if (currentIndex >= queue.size - 1) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(viewModel.translate("No more songs in queue"), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsView(viewModel: MusicViewModel) {
    val isAutoTagEnabled by viewModel.isAutoTagEnabled.collectAsState()
    val skipSilenceEnabled by viewModel.skipSilenceEnabled.collectAsState()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsState()
    val sleepTimerFinishSong by viewModel.sleepTimerFinishSong.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerMillis
    val gridColumns by viewModel.gridColumns.collectAsState()
    val glassEffectEnabled by viewModel.glassEffectEnabled.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                val path = try {
                    val docId = android.provider.DocumentsContract.getTreeDocumentId(it)
                    val split = docId.split(":")
                    if (split[0] == "primary") {
                        android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
                    } else {
                        it.toString() 
                    }
                } catch (e: Exception) {
                    it.toString()
                }
                viewModel.addExcludedFolder(path)
            }
        }
    )

    val yellowSliderColors = SliderDefaults.colors(
        thumbColor = Color.Yellow,
        activeTrackColor = Color.Yellow,
        inactiveTrackColor = Color.Yellow.copy(alpha = 0.3f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(viewModel.translate("Appearance"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        var showGridMenu by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text(viewModel.translate("Grid Columns"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Set how many items wide the Artists and Albums grids should be."), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                Box {
                    TextButton(onClick = { showGridMenu = true }) {
                        Text("$gridColumns " + viewModel.translate("Columns"), color = Color.Yellow)
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.Yellow)
                    }
                    DropdownMenu(
                        expanded = showGridMenu,
                        onDismissRequest = { showGridMenu = false }
                    ) {
                        listOf(2, 3, 4, 5).forEach { cols ->
                            DropdownMenuItem(
                                text = { Text("$cols " + viewModel.translate("Columns")) },
                                onClick = {
                                    viewModel.setGridColumns(cols)
                                    showGridMenu = false
                                }
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        ListItem(
            headlineContent = { Text(viewModel.translate("Glass Effects"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Enable blur and transparency effects (disable for better performance)."), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                Switch(
                    checked = glassEffectEnabled,
                    onCheckedChange = { viewModel.setGlassEffectEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        val dynamicThemingEnabled by viewModel.dynamicThemingEnabled.collectAsState()
        ListItem(
            headlineContent = { Text(viewModel.translate("Dynamic Theming"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Use Android 12+ wallpaper colors for the app theme."), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                Switch(
                    checked = dynamicThemingEnabled,
                    onCheckedChange = { viewModel.setDynamicThemingEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Yellow.copy(alpha = 0.2f))

        Text(viewModel.translate("Audio"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text(viewModel.translate("Equalizer"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Adjust frequency bands, bass boost, and virtualizer."), color = Color.Yellow.copy(alpha = 0.7f)) },
            leadingContent = { Icon(AppIcons.Tune, null, tint = Color.Yellow) },
            modifier = Modifier.clickable { viewModel.setView(MusicViewModel.View.EQUALIZER) },
            trailingContent = { Icon(AppIcons.ChevronRight, null, tint = Color.Yellow) },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        ListItem(
            headlineContent = { Text(viewModel.translate("Silence Trimming"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Automatically skip silence at the start and end of tracks."), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                Switch(
                    checked = skipSilenceEnabled,
                    onCheckedChange = { viewModel.setSkipSilenceEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        ListItem(
            headlineContent = { Text(viewModel.translate("Gapless Playback"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Transition seamlessly between tracks."), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                val gaplessEnabled by viewModel.gaplessPlaybackEnabled.collectAsState()
                Switch(
                    checked = gaplessEnabled,
                    onCheckedChange = { viewModel.setGaplessPlaybackEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        val replayGainEnabled by viewModel.replayGainEnabled.collectAsState()
        ListItem(
            headlineContent = { Text(viewModel.translate("ReplayGain"), color = Color.Yellow) },
            supportingContent = { 
                val isScanning by viewModel.isScanningGain.collectAsState()
                Column {
                    Text(viewModel.translate("Normalize volume across tracks using metadata tags."), color = Color.Yellow.copy(alpha = 0.7f))
                    if (isScanning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Color.Yellow)
                    } else {
                        TextButton(onClick = { viewModel.scanMissingReplayGain() }) {
                            Text(viewModel.translate("Scan Missing Gain Tags"), color = Color.Yellow)
                        }
                    }
                }
            },
            trailingContent = {
                Switch(
                    checked = replayGainEnabled,
                    onCheckedChange = { viewModel.setReplayGainEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        val bitPerfectEnabled by viewModel.bitPerfectEnabled.collectAsState()
        ListItem(
            headlineContent = { Text(viewModel.translate("Bit-Perfect Output"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Bypass internal processing for external USB DACs."), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                Switch(
                    checked = bitPerfectEnabled,
                    onCheckedChange = { viewModel.setBitPerfectEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Yellow.copy(alpha = 0.2f))

        Text(viewModel.translate("Sleep Timer"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        if (sleepTimerRemaining > 0) {
            ListItem(
                headlineContent = { Text(viewModel.translate("Active") + ": ${formatTime(sleepTimerRemaining)} " + viewModel.translate("remaining"), color = Color.Yellow) },
                trailingContent = {
                    TextButton(onClick = { viewModel.cancelSleepTimer() }) {
                        Text(viewModel.translate("Stop"), color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(5, 15, 30, 60).forEach { mins ->
                OutlinedButton(
                    onClick = { viewModel.startSleepTimer(mins) },
                    border = BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Yellow)
                ) {
                    Text("${mins}m")
                }
            }
        }

        ListItem(
            headlineContent = { Text(viewModel.translate("Sleep Timer: Finish current song"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Wait for the currently playing song to end before stopping."), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                Switch(
                    checked = sleepTimerFinishSong,
                    onCheckedChange = { viewModel.setSleepTimerFinishSong(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Yellow.copy(alpha = 0.2f))

        Text(viewModel.translate("Library"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text(viewModel.translate("Online Features"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Enable internet-reliant features (metadata fetching, YouTube search, lyrics etc.)"), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                val isOnline by viewModel.isOnlineMode.collectAsState()
                Switch(
                    checked = isOnline,
                    onCheckedChange = { viewModel.setOnlineMode(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        ListItem(
            headlineContent = { Text(viewModel.translate("App UI Translation"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Automatically translate the app interface into your language using Google Translate."), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                val isAppTranslationEnabled by viewModel.isAppTranslationEnabled.collectAsState()
                Switch(
                    checked = isAppTranslationEnabled,
                    onCheckedChange = { viewModel.setAppTranslationEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )
        
        ListItem(
            headlineContent = { Text(viewModel.translate("Auto-write to disk"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Write corrected artwork and text tags directly to original music files."), color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                Switch(
                    checked = isAutoTagEnabled,
                    onCheckedChange = { viewModel.setAutoTagEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        Button(
            onClick = { viewModel.batchFixMetadata() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow.copy(alpha = 0.1f), contentColor = Color.Yellow)
        ) {
            Icon(AppIcons.AutoFixHigh, null)
            Spacer(Modifier.width(8.dp))
            Text(viewModel.translate("Batch Fix Metadata (Auto)"))
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))

        Text(viewModel.translate("Connectivity"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        val isStreaming by viewModel.isStreaming.collectAsState()
        val streamUrl = viewModel.getStreamingUrl()

        ListItem(
            headlineContent = { Text(viewModel.translate("Share to Browser (Wi-Fi)"), color = Color.Yellow) },
            supportingContent = { 
                if (isStreaming && streamUrl != null) {
                    Text("Live at: $streamUrl", color = Color.Green, fontWeight = FontWeight.Bold)
                } else {
                    Text(viewModel.translate("Stream your music to any device on your Wi-Fi."), color = Color.Yellow.copy(alpha = 0.7f))
                }
            },
            trailingContent = {
                Switch(
                    checked = isStreaming,
                    onCheckedChange = { viewModel.toggleStreaming() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))
        
        Text(viewModel.translate("Playback"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(viewModel.translate("Crossfade Duration") + ": ${crossfadeDuration}s", style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
            Slider(
                value = crossfadeDuration.toFloat(),
                onValueChange = { viewModel.setCrossfadeDuration(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9,
                colors = yellowSliderColors
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))
        
        Text(viewModel.translate("Library Blacklist"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))
        Text(viewModel.translate("Songs in these folders will be hidden from your library."), style = MaterialTheme.typography.bodySmall, color = Color.Yellow.copy(alpha = 0.5f))
        
        val excludedFolders by viewModel.excludedFolders.collectAsState()
        val ignoreNoMedia by viewModel.ignoreNoMedia.collectAsState()

        ListItem(
            headlineContent = { Text(viewModel.translate("Ignore .nomedia files"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("Scan folders even if they contain a .nomedia file."), color = Color.Yellow.copy(alpha = 0.5f)) },
            trailingContent = {
                Switch(
                    checked = ignoreNoMedia,
                    onCheckedChange = { viewModel.setIgnoreNoMedia(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.1f))
        )
        
        excludedFolders.forEach { folder ->
            ListItem(
                headlineContent = { Text(folder, color = Color.Yellow) },
                trailingContent = {
                    IconButton(onClick = { viewModel.removeExcludedFolder(folder) }) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.1f))
            )
        }
        
        Button(
            onClick = { folderPickerLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow.copy(alpha = 0.1f), contentColor = Color.Yellow)
        ) {
            Icon(AppIcons.Folder, null)
            Spacer(Modifier.width(8.dp))
            Text(viewModel.translate("Select Folder to Blacklist"))
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))
        
        Text(viewModel.translate("Cache"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { viewModel.clearCache() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f), contentColor = Color.White)
        ) {
            Text(viewModel.translate("Clear Metadata Cache"))
        }

        Spacer(Modifier.height(16.dp))

        Text(viewModel.translate("Backup & Sync"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
            onResult = { uri -> uri?.let { viewModel.exportLibraryData(it) } }
        )
        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri -> uri?.let { viewModel.importLibraryData(it) } }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { exportLauncher.launch("music_library_backup.json") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow.copy(alpha = 0.1f), contentColor = Color.Yellow)
            ) {
                Icon(Icons.Default.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text(viewModel.translate("Export"))
            }
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow.copy(alpha = 0.1f), contentColor = Color.Yellow)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(viewModel.translate("Import"))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))

        Text(viewModel.translate("App Info"), style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text(viewModel.translate("About Music Player"), color = Color.Yellow) },
            supportingContent = { Text(viewModel.translate("View credits, used libraries, and data sources."), color = Color.Yellow.copy(alpha = 0.7f)) },
            leadingContent = { Icon(Icons.Default.Info, null, tint = Color.Yellow) },
            modifier = Modifier.clickable { viewModel.setView(MusicViewModel.View.ABOUT) },
            trailingContent = { Icon(AppIcons.ChevronRight, null, tint = Color.Yellow) },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun AboutView(viewModel: MusicViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = AppIcons.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Yellow
        )
        Text(
            text = "Music Player",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow
        )
        Text(
            text = viewModel.translate("Version") + " 1.0.4",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Yellow.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = viewModel.translate("Data Sources"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(8.dp))
        AboutCard(
            title = "MusicBrainz",
            description = viewModel.translate("The primary source for high-quality music metadata, artist details, and release information.")
        )
        AboutCard(
            title = "TheAudioDB",
            description = viewModel.translate("Used for artist biographies and extended discography data.")
        )
        AboutCard(
            title = "AcoustID",
            description = viewModel.translate("Audio fingerprinting service used to identify unknown local files.")
        )
        AboutCard(
            title = "iTunes Search API",
            description = viewModel.translate("Source for high-resolution album artwork and genre classifications.")
        )
        AboutCard(
            title = "LRCLib & Netease",
            description = viewModel.translate("Providers for synchronized and plain-text lyrics.")
        )
        AboutCard(
            title = "YouTube",
            description = viewModel.translate("Used for streaming previews and downloading missing tracks.")
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = viewModel.translate("Open Source Libraries"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(8.dp))
        AboutCard(
            title = "NewPipe Extractor",
            description = viewModel.translate("Powerful library for extracting media information and streams from YouTube.")
        )
        AboutCard(
            title = "TagLib",
            description = viewModel.translate("Advanced library for reading and writing ID3 tags and other audio metadata.")
        )
        AboutCard(
            title = "Coil",
            description = viewModel.translate("Fast and lightweight image loading library for Android.")
        )
        AboutCard(
            title = "Media3 (ExoPlayer)",
            description = viewModel.translate("The industry standard for high-performance audio playback on Android.")
        )
        AboutCard(
            title = "Retrofit & OkHttp",
            description = viewModel.translate("The backbone for all networking and API communications.")
        )
        AboutCard(
            title = "Room Database",
            description = viewModel.translate("Robust local storage for your library's metadata cache and settings.")
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = viewModel.translate("Project"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(8.dp))
        val context = LocalContext.current
        AboutCard(
            title = viewModel.translate("Source Code"),
            description = "https://github.com/steel101/music-player-",
            onClick = {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/steel101/music-player-"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "No browser found to open link.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(Modifier.height(32.dp))
        Text(
            text = viewModel.translate("Made with ❤️ by steel101"),
            style = MaterialTheme.typography.labelLarge,
            color = Color.Yellow.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun AboutCard(title: String, description: String, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.Yellow)
            Spacer(Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

fun Modifier.swipeToSkip(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier = this.pointerInput(Unit) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onDragEnd = {
            if (totalDrag < -100f) {
                onSwipeLeft()
            } else if (totalDrag > 100f) {
                onSwipeRight()
            }
        },
        onDragCancel = { totalDrag = 0f },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            totalDrag += dragAmount
        }
    )
}

fun Modifier.swipeToDismiss(
    onSwipeDown: () -> Unit
): Modifier = this.pointerInput(Unit) {
    var totalDrag = 0f
    detectVerticalDragGestures(
        onDragStart = { totalDrag = 0f },
        onDragEnd = {
            if (totalDrag > 150f) {
                onSwipeDown()
            }
        },
        onDragCancel = { totalDrag = 0f },
        onVerticalDrag = { change, dragAmount ->
            change.consume()
            totalDrag += dragAmount
        }
    )
}

@Composable
fun FullResolutionArtViewer(
    imageSource: Any,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageSource,
                contentDescription = "Full Resolution Artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}

