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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.steel101.musicplayer.data.PlaylistEntity
import com.steel101.musicplayer.data.Song
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

    val animatedBgColor by animateColorAsState(
        targetValue = Color(dominantColor).copy(alpha = 0.98f),
        animationSpec = tween(durationMillis = 1000),
        label = "bgColorTransition"
    )

    val playlists by viewModel.playlists.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
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

    BackHandler(enabled = isSearchActive || showFullPlayer || currentView != MusicViewModel.View.SONGS) {
        if (isSearchActive) {
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
                MusicViewModel.View.ABOUT -> viewModel.setView(MusicViewModel.View.SONGS)

                else -> {}
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    NavigationDrawerItem(
                        label = { Text("All Songs") },
                        selected = currentView == MusicViewModel.View.SONGS,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.SONGS)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(AppIcons.MusicNote, null) }
                    )
                    NavigationDrawerItem(
                        label = { Text("Artists") },
                        selected = currentView == MusicViewModel.View.ARTISTS,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.ARTISTS)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Person, null) }
                    )
                    NavigationDrawerItem(
                        label = { Text("Albums") },
                        selected = currentView == MusicViewModel.View.ALBUMS,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.ALBUMS)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(AppIcons.Album, null) }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        label = { Text("Library") },
                        selected = false,
                        onClick = { expandedLibrary = !expandedLibrary },
                        icon = { Icon(if (expandedLibrary) AppIcons.ExpandLess else AppIcons.ExpandMore, null) },
                        badge = { Icon(AppIcons.LibraryMusic, null) }
                    )

                    AnimatedVisibility(visible = expandedLibrary) {
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            NavigationDrawerItem(
                                label = { Text("Genres") },
                                selected = currentView == MusicViewModel.View.GENRES,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.GENRES)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.Label, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("Folders") },
                                selected = currentView == MusicViewModel.View.FOLDERS,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.FOLDERS)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.Folder, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("Decades") },
                                selected = currentView == MusicViewModel.View.DECADES,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.DECADES)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.Event, null) }
                            )
                        }
                    }

                    NavigationDrawerItem(
                        label = { Text("Podcasts") },
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
                        label = { Text("Favorites") },
                        selected = currentView == MusicViewModel.View.FAVORITES,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.FAVORITES)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Favorite, null) }
                    )
                    NavigationDrawerItem(
                        label = { Text("Playlists") },
                        selected = currentView == MusicViewModel.View.PLAYLISTS,
                        onClick = {
                            viewModel.setView(MusicViewModel.View.PLAYLISTS)
                            showFullPlayer = false
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(AppIcons.PlaylistPlay, null) }
                    )
                    
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        label = { Text("Stats & History") },
                        selected = false,
                        onClick = { expandedStats = !expandedStats },
                        icon = { Icon(if (expandedStats) AppIcons.ExpandLess else AppIcons.ExpandMore, null) },
                        badge = { Icon(AppIcons.BarChart, null) }
                    )

                    AnimatedVisibility(visible = expandedStats) {
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            NavigationDrawerItem(
                                label = { Text("Recently Played") },
                                selected = currentView == MusicViewModel.View.RECENTLY_PLAYED,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.RECENTLY_PLAYED)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.History, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("Most Played") },
                                selected = currentView == MusicViewModel.View.MOST_PLAYED,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.MOST_PLAYED)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.BarChart, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("Recently Added") },
                                selected = currentView == MusicViewModel.View.RECENTLY_ADDED,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.RECENTLY_ADDED)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.NewReleases, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("Never Played") },
                                selected = currentView == MusicViewModel.View.NEVER_PLAYED,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.NEVER_PLAYED)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.QuestionMark, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("Forgotten Favorites") },
                                selected = currentView == MusicViewModel.View.FORGOTTEN_FAVORITES,
                                onClick = {
                                    viewModel.setView(MusicViewModel.View.FORGOTTEN_FAVORITES)
                                    showFullPlayer = false
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(AppIcons.HistoryEdu, null) }
                            )
                            NavigationDrawerItem(
                                label = { Text("Top Played") },
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
                        label = { Text("Insights") },
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
                            label = { Text("YouTube Search") },
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
                        label = { Text("Settings") },
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
        val bgColor = if (showFullPlayer) {
            animatedBgColor
        } else {
            Color.DarkGray
        }
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
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
                                if (isSearchActive) {
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
                                            Text(
                                                when (currentView) {
                                                    MusicViewModel.View.SONGS -> "Songs"
                                                    MusicViewModel.View.ARTISTS -> "Artists"
                                                    MusicViewModel.View.ALBUMS -> "Albums"
                                                    MusicViewModel.View.ARTIST_DETAIL -> selectedArtist
                                                        ?: "Artist"

                                                    MusicViewModel.View.ALBUM_DETAIL -> selectedAlbum
                                                        ?: "Album"

                                                    MusicViewModel.View.GENRE_DETAIL -> selectedGenre
                                                        ?: "Genre"

                                                    MusicViewModel.View.DECADE_DETAIL -> "${selectedDecade}s"

                                                    MusicViewModel.View.PLAYLIST_DETAIL -> selectedPlaylist?.name
                                                        ?: "Playlist"

                                                    MusicViewModel.View.GENRES -> "Genres"
                                                    MusicViewModel.View.FOLDERS -> "Folders"
                                                    MusicViewModel.View.INSIGHTS -> "Library Insights"
                                                    MusicViewModel.View.YT_SEARCH -> "YouTube Search"

                                                    MusicViewModel.View.FAVORITES -> "Favorites"
                                                    MusicViewModel.View.PLAYLISTS -> "Playlists"
                                                    MusicViewModel.View.RECENTLY_PLAYED -> "Recently Played"
                                                    MusicViewModel.View.MOST_PLAYED -> "Most Played"
                                                    MusicViewModel.View.RECENTLY_ADDED -> "Recently Added"
                                                    MusicViewModel.View.NEVER_PLAYED -> "Never Played"
                                                    MusicViewModel.View.FORGOTTEN_FAVORITES -> "Forgotten Favorites"
                                                    MusicViewModel.View.PODCASTS -> "Podcasts"
                                                    MusicViewModel.View.EQUALIZER -> "Equalizer"
                                                    MusicViewModel.View.SETTINGS -> "Settings"
                                                    MusicViewModel.View.ABOUT -> "About"
                                                    MusicViewModel.View.QUEUE -> "Up Next"
                                                    else -> "Music Player"
                                                }
                                            )
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
                                                        Icon(AppIcons.Sort, "Sort")
                                                    }
                                                    DropdownMenu(
                                                        expanded = showSortMenu,
                                                        onDismissRequest = { showSortMenu = false }
                                                    ) {
                                                        MusicViewModel.SortOrder.values().forEach { order ->
                                                            DropdownMenuItem(
                                                                text = { Text(order.name.replace("_", " ").lowercase().capitalize()) },
                                                                onClick = {
                                                                    viewModel.setSortOrder(order)
                                                                    showSortMenu = false
                                                                }
                                                            )
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
                                                            contentDescription = "Rescan Library"
                                                        )
                                                    }
                                                }
                                                IconButton(onClick = { isSearchActive = true }) {
                                                    Icon(
                                                        Icons.Default.Search,
                                                        contentDescription = "Search"
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            },
                            bottomBar = {
                                if (currentSong != null) {
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
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            LazyColumn(
                                                state = songListState,
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(
                                                    horizontal = 16.dp,
                                                    vertical = 8.dp
                                                ),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                item {
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
                                                        imageUrl = headerImage
                                                    )
                                                }
                                                items(songs) { song ->
                                                    SongItem(
                                                        song = song,
                                                        isSelected = song.id == currentSong?.id,
                                                        onClick = { viewModel.playSong(song, songs) },
                                                        onLongClick = { songOptions = song },
                                                        onFavoriteToggle = {
                                                            viewModel.toggleFavorite(
                                                                song
                                                            )
                                                        }
                                                    )
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
                                                        .background(
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                                alpha = 0.2f
                                                            )
                                                        )
                                                        .padding(vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.SpaceEvenly
                                                ) {
                                                    availableLetters.forEach { letter ->
                                                        Text(
                                                            text = letter.toString(),
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = MaterialTheme.colorScheme.primary,
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
                                                                            songListState.scrollToItem(
                                                                                index + 1
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

                                        Row(modifier = Modifier.fillMaxSize()) {
                                            LazyVerticalGrid(
                                                state = artistGridState,
                                                columns = GridCells.Fixed(gridColumns),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                items(artists) { artist ->
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

                                            if (artists.size > 5 && availableLetters.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .width(28.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                                alpha = 0.2f
                                                            )
                                                        )
                                                        .padding(vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.SpaceEvenly
                                                ) {
                                                    availableLetters.forEach { letter ->
                                                        Text(
                                                            text = letter.toString(),
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = MaterialTheme.colorScheme.primary,
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

                                        Row(modifier = Modifier.fillMaxSize()) {
                                            LazyVerticalGrid(
                                                state = albumGridState,
                                                columns = GridCells.Fixed(gridColumns),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                items(albums) { album ->
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

                                            if (albums.size > 5 && availableLetters.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .width(28.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                                alpha = 0.2f
                                                            )
                                                        )
                                                        .padding(vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.SpaceEvenly
                                                ) {
                                                    availableLetters.forEach { letter ->
                                                        Text(
                                                            text = letter.toString(),
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = MaterialTheme.colorScheme.primary,
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
                                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                            Button(
                                                onClick = { showCreatePlaylistDialog = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Add, null)
                                                Text("Create New Playlist")
                                            }
                                            Spacer(Modifier.height(16.dp))
                                            LazyColumn {
                                                items(playlists) { playlist ->
                                                    PlaylistListItem(
                                                        playlist = playlist,
                                                        onClick = {
                                                            viewModel.setView(
                                                                MusicViewModel.View.PLAYLIST_DETAIL,
                                                                playlist = playlist
                                                            )
                                                        },
                                                        onLongClick = {
                                                            playlistToDelete = playlist
                                                        }
                                                    )
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

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(genres) { genre ->
                                                ListItem(
                                                    headlineContent = { Text(genre.name) },
                                                    supportingContent = { Text("${genre.songs.size} songs") },
                                                    leadingContent = { Icon(AppIcons.Label, null) },
                                                    modifier = Modifier.clickable {
                                                        viewModel.setView(MusicViewModel.View.GENRE_DETAIL, genre = genre.name)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    MusicViewModel.View.DECADES -> {
                                        val decades = remember(songs) {
                                            songs.groupBy { if (it.year > 0) (it.year / 10) * 10 else 0 }
                                                .map { (decade, songs) -> decade to songs }
                                                .sortedByDescending { it.first }
                                        }

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(decades) { (decade, decadeSongs) ->
                                                ListItem(
                                                    headlineContent = { Text(if (decade > 0) "${decade}s" else "Unknown Era", color = Color.Yellow) },
                                                    supportingContent = { Text("${decadeSongs.size} songs", color = Color.Yellow.copy(alpha = 0.7f)) },
                                                    leadingContent = { Icon(AppIcons.Event, null, tint = Color.Yellow) },
                                                    modifier = Modifier.clickable {
                                                        viewModel.setView(MusicViewModel.View.DECADE_DETAIL, decade = decade)
                                                    },
                                                    colors = ListItemDefaults.colors(containerColor = Color.DarkGray)
                                                )
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

                                    MusicViewModel.View.ABOUT -> {
                                        AboutView()
                                    }

                                    else -> {}
                                }
                            }
                        }
                    }
                }
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
                        title = { Text("New Playlist") },
                        text = {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Playlist Name") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (name.isNotBlank()) viewModel.createPlaylist(name)
                                showCreatePlaylistDialog = false
                            }) { Text("Create") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showCreatePlaylistDialog = false
                            }) { Text("Cancel") }
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
                        containerColor = Color(dominantColor).copy(alpha = 0.95f),
                        contentColor = Color.White
                    ) {
                        QueueView(viewModel = viewModel)
                    }
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (viewingAlbumTitle != null) {
                    IconButton(onClick = { viewingAlbumTitle = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
                Text(viewingAlbumTitle ?: artistName)
            }
        },
        text = {
            if (!isOnline) {
                Text("Online Features disabled. Go to Settings to enable.")
            } else if (isLoadingBio || (viewingAlbumTitle != null && isLoadingTracks)) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (viewingAlbumTitle != null) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (mbTracks.isEmpty()) {
                        Text("No tracks found on MusicBrainz for this release.", modifier = Modifier.padding(16.dp))
                    } else {
                        mbTracks.forEach { track ->
                            ListItem(
                                headlineContent = { Text("${track.position}. ${track.title}") },
                                supportingContent = {
                                    val localSongs by viewModel.songs.collectAsState()
                                    val inLibrary = localSongs.any {
                                        it.title.equals(track.title, ignoreCase = true) &&
                                                it.artist.contains(artistName, ignoreCase = true)
                                    }
                                    if (inLibrary) {
                                        Text("In Library", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                },
                                trailingContent = {
                                    val localSongs by viewModel.songs.collectAsState()
                                    val inLibrary = localSongs.any {
                                        it.title.equals(track.title, ignoreCase = true) &&
                                                it.artist.contains(artistName, ignoreCase = true)
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.playPreview(track.title ?: "", artistName) }) {
                                            Icon(Icons.Default.PlayArrow, "Preview", tint = Color.Yellow)
                                        }

                                        if (!inLibrary) {
                                            val trackKey = "$artistName - ${track.title}"
                                            val progress = downloadingTracks[trackKey]
                                            val isTrackDownloading = progress != null
                                            
                                            if (isTrackDownloading) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                                                    CircularProgressIndicator(
                                                        progress = { progress ?: 0f },
                                                        modifier = Modifier.fillMaxSize(),
                                                        strokeWidth = 3.dp,
                                                        color = Color.Yellow,
                                                        trackColor = Color.Black.copy(alpha = 0.2f)
                                                    )
                                                    Text(
                                                        text = "${((progress ?: 0f) * 100).toInt()}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Yellow
                                                    )
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { viewModel.downloadFromYoutube(track.title ?: "", artistName) }
                                                ) {
                                                    Icon(AppIcons.Download, "Download from YouTube", tint = Color.Yellow)
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (bio != null) {
                        Text(
                            text = "Biography",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = bio!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    if (albums.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Discography (Tap to see tracklist from MusicBrainz)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        albums.forEach { album ->
                            ListItem(
                                headlineContent = { Text(album.title ?: "Unknown Album") },
                                supportingContent = { Text(album.year ?: "") },
                                leadingContent = {
                                    AsyncImage(
                                        model = album.thumbUrl,
                                        contentDescription = album.title,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                        placeholder = rememberVectorPainter(AppIcons.Album),
                                        error = rememberVectorPainter(AppIcons.Album)
                                    )
                                },
                                modifier = Modifier.clickable {
                                    album.title?.let {
                                        viewingAlbumTitle = it
                                        viewModel.fetchAlbumTracksFromMusicBrainz(it, artistName)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun YouTubeSearchView(viewModel: MusicViewModel) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.ytSearchResults.collectAsState()
    val isSearching by viewModel.isSearchingYt
    val downloadingTracks by viewModel.downloadingTracks.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search YouTube") },
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
        
        Spacer(Modifier.height(16.dp))
        
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results) { item ->
                    val trackKey = "${item.uploaderName} - ${item.name}"
                    val progress = downloadingTracks[trackKey]
                    
                    ListItem(
                        headlineContent = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(item.uploaderName ?: "Unknown Artist", maxLines = 1) },
                        leadingContent = {
                            AsyncImage(
                                model = item.thumbnails?.firstOrNull()?.url,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop,
                                placeholder = rememberVectorPainter(AppIcons.MusicNote),
                                error = rememberVectorPainter(AppIcons.MusicNote)
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { 
                                    viewModel.playYoutubePreview(item) 
                                }) {
                                    Icon(Icons.Default.PlayArrow, "Preview", tint = Color.Yellow)
                                }
                                
                                if (progress != null) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                                        CircularProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxSize(),
                                            strokeWidth = 3.dp,
                                            color = Color.Yellow,
                                            trackColor = Color.Black.copy(alpha = 0.2f)
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { 
                                        viewModel.downloadFromYoutube(item.name, item.uploaderName ?: "Unknown") 
                                    }) {
                                        Icon(AppIcons.Download, null, tint = Color.Yellow)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SongsHeader(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    imageUrl: Any? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (imageUrl != null || title != null) {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(AppIcons.MusicNote),
                        error = rememberVectorPainter(AppIcons.MusicNote)
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Play All")
            }
            FilledTonalButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Shuffle, null)
                Spacer(Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistListItem(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(playlist.name) },
        leadingContent = { Icon(AppIcons.PlaylistPlay, null) },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    )
}


@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    onPlaylistSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
    onDismiss: () -> Unit,
    isOnline: Boolean = true
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("Play Next") },
                    leadingContent = { Icon(AppIcons.PlaylistPlay, null) },
                    modifier = Modifier.clickable { onPlayNext() }
                )
                ListItem(
                    headlineContent = { Text("Add to Queue") },
                    leadingContent = { Icon(AppIcons.PlaylistPlay, null) },
                    modifier = Modifier.clickable { onAddToQueue() }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text("Go to Artist") },
                    supportingContent = { Text(song.artist) },
                    leadingContent = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.clickable { onGoToArtist() }
                )
                ListItem(
                    headlineContent = { Text("Go to Album") },
                    supportingContent = { Text(song.album) },
                    leadingContent = { Icon(AppIcons.Album, null) },
                    modifier = Modifier.clickable { onGoToAlbum() }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text(if (song.manualNotPodcast) "Set as Podcast" else "Set as Song") },
                    leadingContent = { Icon(if (song.manualNotPodcast) AppIcons.Podcasts else AppIcons.MusicNote, null) },
                    modifier = Modifier.clickable { onToggleNotPodcast() }
                )
                ListItem(
                    headlineContent = { Text("Fix Metadata / Artwork") },
                    leadingContent = { Icon(AppIcons.Edit, null) },
                    modifier = Modifier.clickable { onFixMetadata() }
                )
                ListItem(
                    headlineContent = { Text("Identify Song (AcoustID)", color = if (isOnline) Color.Unspecified else Color.Gray) },
                    leadingContent = { Icon(AppIcons.AutoFixHigh, null, tint = if (isOnline) LocalContentColor.current else Color.Gray) },
                    modifier = Modifier.clickable { 
                        if (isOnline) onIdentifySong() 
                        else android.widget.Toast.makeText(context, "Online Features disabled. Go to Settings to enable.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                ListItem(
                    headlineContent = { Text(if (isOnline) "Set Artwork from Local / URL" else "Set Artwork from Local") },
                    leadingContent = { Icon(AppIcons.Image, null) },
                    modifier = Modifier.clickable { onSetArtwork() }
                )
                ListItem(
                    headlineContent = { Text("Set Genre") },
                    leadingContent = { Icon(AppIcons.Label, null) },
                    modifier = Modifier.clickable { onSetGenre() }
                )
                ListItem(
                    headlineContent = { Text("Add to Playlist") },
                    leadingContent = { Icon(AppIcons.PlaylistPlay, null) },
                    modifier = Modifier.clickable { onAddToPlaylist() }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text("Delete from Device", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onDelete() }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun GenreEditDialog(
    song: Song,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var genre by remember { mutableStateOf(song.genre ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Genre") },
        text = {
            OutlinedTextField(
                value = genre,
                onValueChange = { genre = it },
                label = { Text("Genre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(genre) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
        Text("Library Insights", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.Yellow)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Total Listening Time", style = MaterialTheme.typography.titleSmall, color = Color.Yellow.copy(alpha = 0.7f))
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
                    Text("Top Artist", style = MaterialTheme.typography.titleSmall, color = Color.Yellow.copy(alpha = 0.7f))
                    Text(topArtist, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Yellow)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Top Genre", style = MaterialTheme.typography.titleSmall, color = Color.Yellow.copy(alpha = 0.7f))
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
                Text("Total Plays", style = MaterialTheme.typography.titleSmall, color = Color.Yellow.copy(alpha = 0.7f))
                Text(totalPlays.toString(), style = MaterialTheme.typography.headlineMedium, color = Color.Yellow)
            }
        }

        if (forgottenGem != null) {
            Spacer(Modifier.height(24.dp))
            Text("Forgotten Gem", style = MaterialTheme.typography.titleMedium, color = Color.Yellow, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.playSong(forgottenGem) },
                colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.1f))
            ) {
                ListItem(
                    headlineContent = { Text(forgottenGem.title, color = Color.Yellow) },
                    supportingContent = { Text("Last played ${formatDate(forgottenGem.lastPlayed)}", color = Color.Yellow.copy(alpha = 0.7f)) },
                    leadingContent = { Icon(AppIcons.History, null, tint = Color.Yellow) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        if (topSongs.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Top 5 Songs", style = MaterialTheme.typography.titleMedium, color = Color.Yellow, modifier = Modifier.align(Alignment.Start))
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
            fontSize = fontSize
        )
        Text(
            text = "${artist.songs.size} songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            fontSize = fontSize
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onClose: (() -> Unit)? = null
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
                
                Text(
                    text = line.text,
                    fontSize = if (isActive) 24.sp else 18.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLineClick(line.timeMs) }
                        .padding(vertical = 12.dp, horizontal = 24.dp)
                        .animateContentSize()
                )
            }
        }

        if (onClose != null) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Lyrics", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        headlineContent = { 
            Text(
                song.title, 
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        supportingContent = { 
            val subtitle = buildString {
                append(song.artist)
                append(" • ")
                append(song.album)
                song.genre?.let {
                    append(" • ")
                    append(it)
                }
            }
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) 
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
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        },
        trailingContent = {
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (song.isFavorite) Color.Red else Color.Gray
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
        )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .swipeToSkip(
                onSwipeLeft = { viewModel.skipNext() },
                onSwipeRight = { viewModel.skipPrevious() }
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
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
                        .size(56.dp)
                        .sharedElement(
                            rememberSharedContentState(key = "artwork"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                        .clip(RoundedCornerShape(4.dp)),
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
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false).basicMarquee()
                    )
                    val sleepTimerRemaining by viewModel.sleepTimerMillis
                    if (sleepTimerRemaining > 0) {
                        Text(
                            text = " • ⏳ ${formatTime(sleepTimerRemaining)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            IconButton(onClick = { viewModel.skipPrevious() }) {
                Icon(AppIcons.SkipPrevious, contentDescription = null)
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { viewModel.skipNext() }) {
                Icon(AppIcons.SkipNext, contentDescription = null)
            }
        }
    }
}

@Composable
fun Visualizer(viewModel: MusicViewModel) {
    val barCount = 32
    val magnitudes by viewModel.visualizerData
    val isPlaying by viewModel.isPlaying
    
    val rainbowColors = listOf(
        Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00),
        Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFF8B00FF)
    )

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
            
            val colorIndex = (index.toFloat() / barCount * rainbowColors.size).toInt()
            val barColor = rainbowColors[colorIndex.coerceIn(0, rainbowColors.size - 1)]

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
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val playbackPosition by viewModel.playbackPosition
    val showLyrics by viewModel.showLyrics
    val lyrics by viewModel.currentLyrics
    val repeatMode by viewModel.repeatMode
    val shuffleMode by viewModel.shuffleMode
    val backImageUrl by viewModel.backImageUrl
    val isOnline by viewModel.isOnlineMode.collectAsState()
    
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
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
                    .background(Color.White.copy(alpha = 0.8f))
            )
        }

        var showLyricsEditor by remember { mutableStateOf(false) }
        if (showLyricsEditor) {
            LyricsEditorDialog(
                song = song,
                viewModel = viewModel,
                onDismiss = { showLyricsEditor = false }
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = showLyrics && lyrics.isNotEmpty(),
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "lyricsTransition"
            ) { isShowingLyrics ->
                if (isShowingLyrics) {
                    SyncedLyricsView(
                        lyrics = lyrics,
                        currentPosition = playbackPosition,
                        onLineClick = { timeMs -> viewModel.seekTo(timeMs) },
                        modifier = Modifier.fillMaxSize(),
                        onClose = { viewModel.toggleLyrics() }
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(16.dp))
                        val albumArtUri = if (song.hasEmbeddedArt) {
                            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                        } else {
                            song.albumImageUrl?.let { Uri.parse(it) }
                        }

                        var isFlipped by remember { mutableStateOf(false) }
                        val rotation by animateFloatAsState(
                            targetValue = if (isFlipped) 180f else 0f,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                            label = "artworkFlip"
                        )

                        with(sharedTransitionScope) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        rotationY = rotation
                                        cameraDistance = 12f * density
                                    }
                                    .combinedClickable(
                                        onClick = { 
                                            if (isOnline) {
                                                onShowArtistBio(song.artist) 
                                            } else {
                                                android.widget.Toast.makeText(context, "Online Features disabled. Go to Settings to enable.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onLongClick = { if (backImageUrl != null) isFlipped = !isFlipped }
                                    )
                            ) {
                                if (rotation <= 90f) {
                                    AsyncImage(
                                        model = albumArtUri,
                                        contentDescription = null,
                                        placeholder = rememberVectorPainter(AppIcons.MusicNote),
                                        error = rememberVectorPainter(AppIcons.MusicNote),
                                        fallback = rememberVectorPainter(AppIcons.MusicNote),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .sharedElement(
                                                rememberSharedContentState(key = "artwork"),
                                                animatedVisibilityScope = animatedVisibilityScope
                                            )
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.DarkGray)
                                            .swipeToSkip(
                                                onSwipeLeft = { viewModel.skipNext() },
                                                onSwipeRight = { viewModel.skipPrevious() }
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    AsyncImage(
                                        model = backImageUrl,
                                        contentDescription = "Back Cover",
                                        placeholder = rememberVectorPainter(AppIcons.MusicNote),
                                        error = rememberVectorPainter(AppIcons.MusicNote),
                                        fallback = rememberVectorPainter(AppIcons.MusicNote),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { rotationY = 180f }
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.DarkGray),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                        
                        if (backImageUrl != null) {
                            Text(
                                text = if (isFlipped) "Back Cover" else "Front Cover (Tap for Bio, Long press to flip)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Text(
                                text = "Tap for Artist Biography",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = song.title,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                        )
                        Text(
                            text = song.artist,
                            fontSize = 20.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                        )
                        
                        Spacer(Modifier.weight(0.1f))
                        Visualizer(viewModel = viewModel)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        
        Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            val magnitudes by viewModel.visualizerData
            val progress = if (song.duration > 0) playbackPosition.toFloat() / song.duration else 0f

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Slider(
                    value = playbackPosition.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..song.duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Black,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(playbackPosition), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(formatTime(song.duration), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.toggleShuffleMode() },
                modifier = Modifier.requiredSize(48.dp)
            ) {
                Icon(
                    AppIcons.Shuffle, 
                    contentDescription = null, 
                    tint = if (shuffleMode) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
            IconButton(
                onClick = { viewModel.skipPrevious() },
                modifier = Modifier.requiredSize(48.dp)
            ) {
                Icon(AppIcons.SkipPrevious, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White)
            }
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.requiredSize(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Icon(if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            IconButton(
                onClick = { viewModel.skipNext() },
                modifier = Modifier.requiredSize(48.dp)
            ) {
                Icon(AppIcons.SkipNext, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White)
            }
            IconButton(
                onClick = { viewModel.toggleRepeatMode() },
                modifier = Modifier.requiredSize(48.dp)
            ) {
                Icon(
                    when(repeatMode) {
                        Player.REPEAT_MODE_ONE -> AppIcons.RepeatOne
                        Player.REPEAT_MODE_ALL -> AppIcons.Repeat
                        else -> AppIcons.Repeat
                    },
                    contentDescription = null,
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { viewModel.toggleLyrics() },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = AppIcons.Lyrics,
                        contentDescription = null,
                        tint = if (showLyrics) Color.White else Color.White.copy(alpha = 0.5f)
                    )
                    Text("Lyrics", fontSize = 12.sp)
                }
            }

            TextButton(
                onClick = { showLyricsEditor = true },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcons.EditNote, contentDescription = null)
                    Text("Edit Lyrics", fontSize = 12.sp)
                }
            }

            TextButton(
                onClick = { 
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
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text("Share", fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -15) {
                            onShowQueue()
                        }
                    }
                }
                .clickable { onShowQueue() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White.copy(alpha = 0.5f))
                Text("UP NEXT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
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
            Text("Enable Master Effects", style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
            Switch(
                checked = enabled, 
                onCheckedChange = { viewModel.setEqEnabled(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
            )
        }

        Spacer(Modifier.height(24.dp))
        
        Text("Equalizer Presets", style = MaterialTheme.typography.titleSmall, color = Color.Yellow)
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
                Text("Select Preset")
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(
                expanded = showPresetsMenu,
                onDismissRequest = { showPresetsMenu = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                presets.forEach { (name, levels) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            viewModel.applyEqPreset(levels.withIndex().associate { it.index to it.value * 100 })
                            showPresetsMenu = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Frequency Bands", style = MaterialTheme.typography.titleSmall, color = Color.Yellow)
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
        Text("Audio Enhancements", style = MaterialTheme.typography.titleSmall, color = Color.Yellow)
        Spacer(Modifier.height(16.dp))

        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bass Boost", style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
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
                Text("Virtualizer", style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
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

        Text("Playback Control", style = MaterialTheme.typography.titleSmall, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        val speed by viewModel.playbackSpeed.collectAsState()
        val pitch by viewModel.playbackPitch.collectAsState()

        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Speed", style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
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
                Text("Pitch", style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
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
            Text("Reset Speed/Pitch")
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Loudness Limiter", style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
                Text("Prevent audio clipping", style = MaterialTheme.typography.bodySmall, color = Color.Yellow.copy(alpha = 0.5f))
            }
            Switch(
                checked = limiterEnabled, 
                onCheckedChange = { viewModel.setLimiterEnabled(it) }, 
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Yellow, checkedTrackColor = Color.Yellow.copy(alpha = 0.5f))
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text("Reverb Preset", style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true }, 
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Yellow),
                    border = BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.5f))
                ) {
                    Text(reverbPresets.getOrElse(reverbPreset) { "None" })
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    reverbPresets.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
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

@Composable
fun QueueView(viewModel: MusicViewModel) {
    val queue by viewModel.currentQueue.collectAsState()
    val currentSong by viewModel.currentSong
    val currentIndex by viewModel.currentMediaItemIndex

    val nextSongs = remember(queue, currentIndex) {
        if (currentIndex >= 0 && currentIndex < queue.size - 1) {
            queue.subList(currentIndex + 1, queue.size)
        } else {
            emptyList()
        }
    }

    val previousSongs = remember(queue, currentIndex) {
        if (currentIndex > 0) {
            queue.subList(0, currentIndex).reversed()
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
            Text("Play Queue", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                val shuffleMode by viewModel.shuffleMode
                Icon(AppIcons.Shuffle, null, tint = if (shuffleMode) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (nextSongs.isNotEmpty()) {
                itemsIndexed(nextSongs) { indexInSublist, song ->
                    val actualIndex = currentIndex + 1 + indexInSublist
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        ListItem(
                            headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Yellow) },
                            supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Yellow.copy(alpha = 0.7f)) },
                            leadingContent = {
                                val albumArtUri = if (song.hasEmbeddedArt) {
                                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                                } else {
                                    song.albumImageUrl?.let { Uri.parse(it) }
                                }
                                AsyncImage(
                                    model = albumArtUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { viewModel.moveQueueItem(actualIndex, actualIndex - 1) }) {
                                        Icon(AppIcons.ArrowUpward, null, tint = Color.Yellow)
                                    }
                                    IconButton(onClick = { viewModel.removeFromQueue(actualIndex) }) {
                                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
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
                        Text("No more songs in queue", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
        Text("Appearance", style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        var showGridMenu by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("Grid Columns", color = Color.Yellow) },
            supportingContent = { Text("Set how many items wide the Artists and Albums grids should be.", color = Color.Yellow.copy(alpha = 0.7f)) },
            trailingContent = {
                Box {
                    TextButton(onClick = { showGridMenu = true }) {
                        Text("$gridColumns Columns", color = Color.Yellow)
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.Yellow)
                    }
                    DropdownMenu(
                        expanded = showGridMenu,
                        onDismissRequest = { showGridMenu = false }
                    ) {
                        listOf(2, 3, 4, 5).forEach { cols ->
                            DropdownMenuItem(
                                text = { Text("$cols Columns") },
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

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Yellow.copy(alpha = 0.2f))

        Text("Audio", style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("Equalizer", color = Color.Yellow) },
            supportingContent = { Text("Adjust frequency bands, bass boost, and virtualizer.", color = Color.Yellow.copy(alpha = 0.7f)) },
            leadingContent = { Icon(AppIcons.Tune, null, tint = Color.Yellow) },
            modifier = Modifier.clickable { viewModel.setView(MusicViewModel.View.EQUALIZER) },
            trailingContent = { Icon(AppIcons.ChevronRight, null, tint = Color.Yellow) },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )

        ListItem(
            headlineContent = { Text("Silence Trimming", color = Color.Yellow) },
            supportingContent = { Text("Automatically skip silence at the start and end of tracks.", color = Color.Yellow.copy(alpha = 0.7f)) },
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
            headlineContent = { Text("Gapless Playback", color = Color.Yellow) },
            supportingContent = { Text("Transition seamlessly between tracks.", color = Color.Yellow.copy(alpha = 0.7f)) },
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

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Yellow.copy(alpha = 0.2f))

        Text("Sleep Timer", style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        if (sleepTimerRemaining > 0) {
            ListItem(
                headlineContent = { Text("Active: ${formatTime(sleepTimerRemaining)} remaining", color = Color.Yellow) },
                trailingContent = {
                    TextButton(onClick = { viewModel.cancelSleepTimer() }) {
                        Text("Stop", color = MaterialTheme.colorScheme.error)
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
            headlineContent = { Text("Sleep Timer: Finish current song", color = Color.Yellow) },
            supportingContent = { Text("Wait for the currently playing song to end before stopping.", color = Color.Yellow.copy(alpha = 0.7f)) },
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

        Text("Library", style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("Online Features", color = Color.Yellow) },
            supportingContent = { Text("Enable internet-reliant features (metadata fetching, YouTube search, lyrics etc.)", color = Color.Yellow.copy(alpha = 0.7f)) },
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
            headlineContent = { Text("Auto-write to disk", color = Color.Yellow) },
            supportingContent = { Text("Write corrected artwork and text tags directly to original music files.", color = Color.Yellow.copy(alpha = 0.7f)) },
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
            Text("Batch Fix Metadata (Auto)")
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))
        
        Text("Playback", style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Crossfade Duration: ${crossfadeDuration}s", style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
            Slider(
                value = crossfadeDuration.toFloat(),
                onValueChange = { viewModel.setCrossfadeDuration(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9,
                colors = yellowSliderColors
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))
        
        Text("Library Blacklist", style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))
        Text("Songs in these folders will be hidden from your library.", style = MaterialTheme.typography.bodySmall, color = Color.Yellow.copy(alpha = 0.5f))
        
        val excludedFolders by viewModel.excludedFolders.collectAsState()
        
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
            Text("Select Folder to Blacklist")
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))
        
        Text("Cache", style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { viewModel.clearCache() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f), contentColor = Color.White)
        ) {
            Text("Clear Metadata Cache")
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Yellow.copy(alpha = 0.2f))

        Text("App Info", style = MaterialTheme.typography.titleMedium, color = Color.Yellow)
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("About Music Player", color = Color.Yellow) },
            supportingContent = { Text("View credits, used libraries, and data sources.", color = Color.Yellow.copy(alpha = 0.7f)) },
            leadingContent = { Icon(Icons.Default.Info, null, tint = Color.Yellow) },
            modifier = Modifier.clickable { viewModel.setView(MusicViewModel.View.ABOUT) },
            trailingContent = { Icon(AppIcons.ChevronRight, null, tint = Color.Yellow) },
            colors = ListItemDefaults.colors(containerColor = Color.Black.copy(alpha = 0.2f))
        )
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun AboutView() {
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
            text = "Version 1.0.4",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Yellow.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Data Sources",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(8.dp))
        AboutCard(
            title = "MusicBrainz",
            description = "The primary source for high-quality music metadata, artist details, and release information."
        )
        AboutCard(
            title = "TheAudioDB",
            description = "Used for artist biographies and extended discography data."
        )
        AboutCard(
            title = "AcoustID",
            description = "Audio fingerprinting service used to identify unknown local files."
        )
        AboutCard(
            title = "iTunes Search API",
            description = "Source for high-resolution album artwork and genre classifications."
        )
        AboutCard(
            title = "LRCLib & Netease",
            description = "Providers for synchronized and plain-text lyrics."
        )
        AboutCard(
            title = "YouTube",
            description = "Used for streaming previews and downloading missing tracks."
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Open Source Libraries",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(8.dp))
        AboutCard(
            title = "NewPipe Extractor",
            description = "Powerful library for extracting media information and streams from YouTube."
        )
        AboutCard(
            title = "TagLib",
            description = "Advanced library for reading and writing ID3 tags and other audio metadata."
        )
        AboutCard(
            title = "Coil",
            description = "Fast and lightweight image loading library for Android."
        )
        AboutCard(
            title = "Media3 (ExoPlayer)",
            description = "The industry standard for high-performance audio playback on Android."
        )
        AboutCard(
            title = "Retrofit & OkHttp",
            description = "The backbone for all networking and API communications."
        )
        AboutCard(
            title = "Room Database",
            description = "Robust local storage for your library's metadata cache and settings."
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Project",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(8.dp))
        val context = LocalContext.current
        AboutCard(
            title = "Source Code",
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
            text = "Made with ❤️ by steel101",
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

