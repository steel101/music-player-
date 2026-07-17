package com.steel101.musicplayer.ui

import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.palette.graphics.Palette
import com.steel101.musicplayer.data.MetadataCleaner
import com.steel101.musicplayer.data.MusicRepository
import com.steel101.musicplayer.data.Song
import com.steel101.musicplayer.data.PlaylistEntity
import com.steel101.musicplayer.network.AudioDbService
import com.steel101.musicplayer.network.ItunesService
import com.steel101.musicplayer.network.LyricsService
import com.steel101.musicplayer.network.MusicBrainzService
import com.steel101.musicplayer.network.NeteaseService
import com.steel101.musicplayer.player.MusicService
import com.steel101.musicplayer.network.AcoustIdService
import com.geecko.fpcalc.FpCalc
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class MusicViewModel(
    private val repository: MusicRepository,
    private val context: Context
) : ViewModel() {

    private val _pendingWriteRequest = mutableStateOf<PendingIntent?>(null)
    val pendingWriteRequest: State<PendingIntent?> = _pendingWriteRequest

    private val _pendingDeleteRequest = mutableStateOf<PendingIntent?>(null)
    val pendingDeleteRequest: State<PendingIntent?> = _pendingDeleteRequest

    private var songToDelete: Song? = null

    fun consumePendingDeleteRequest() {
        _pendingDeleteRequest.value = null
    }

    private var lastFailedSong: Song? = null
    private var enrichmentJob: Job? = null

    fun consumePendingWriteRequest() {
        _pendingWriteRequest.value = null
    }

    fun retryLastTagWrite() {
        lastFailedSong?.let { performTagWrite(it) }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val sharedPrefs = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
    
    private val _isOnlineMode = MutableStateFlow(sharedPrefs.getBoolean("online_mode", false))
    val isOnlineMode: StateFlow<Boolean> = _isOnlineMode.asStateFlow()

    private val _showOnlineOnboarding = MutableStateFlow(!sharedPrefs.contains("online_mode"))
    val showOnlineOnboarding: StateFlow<Boolean> = _showOnlineOnboarding.asStateFlow()

    fun setOnlineMode(enabled: Boolean) {
        _isOnlineMode.value = enabled
        sharedPrefs.edit().putBoolean("online_mode", enabled).apply()
        _showOnlineOnboarding.value = false
        if (enabled) {
            loadSongs()
            _currentSong.value?.let { fetchLyrics(it) }
        }
    }

    fun dismissOnlineOnboarding() {
        _showOnlineOnboarding.value = false
        if (!sharedPrefs.contains("online_mode")) {
            sharedPrefs.edit().putBoolean("online_mode", false).apply()
        }
    }

    private val _isAutoTagEnabled = MutableStateFlow(sharedPrefs.getBoolean("auto_tag_enabled", false))
    val isAutoTagEnabled: StateFlow<Boolean> = _isAutoTagEnabled.asStateFlow()

    fun setAutoTagEnabled(enabled: Boolean) {
        _isAutoTagEnabled.value = enabled
        sharedPrefs.edit().putBoolean("auto_tag_enabled", enabled).apply()
        if (enabled) {
            loadSongs()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    data class SelectionState(
        val view: View = View.SONGS,
        val artist: String? = null,
        val album: String? = null,
        val playlist: PlaylistEntity? = null,
        val genre: String? = null,
        val decade: Int? = null,
        val playlistPaths: List<String> = emptyList()
    )

    private val _selectionState = MutableStateFlow(SelectionState())
    val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()

    val currentView = _selectionState.map { it.view }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), View.SONGS)
    val selectedArtist = _selectionState.map { it.artist }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val selectedAlbum = _selectionState.map { it.album }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val selectedPlaylist = _selectionState.map { it.playlist }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val selectedGenre = _selectionState.map { it.genre }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val selectedDecade = _selectionState.map { it.decade }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    private fun fuzzyMatch(text: String, query: String): Boolean {
        if (text.contains(query, ignoreCase = true)) return true
        
        val cleanText = text.lowercase().trim()
        val cleanQuery = query.lowercase().trim()
        
        if (cleanQuery.length < 3) return false
        
        val textWords = cleanText.split(" ", "-", "_")
        val queryWords = cleanQuery.split(" ", "-", "_")
        
        for (qw in queryWords) {
            if (qw.isEmpty()) continue
            val match = textWords.any { tw ->
                tw.startsWith(qw) || tw.endsWith(qw) || levenshteinDistance(tw, qw) <= 1
            }
            if (match) return true
        }
        return false
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + 1)
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }

    val filteredSongs: StateFlow<List<Song>> = combine(_songs, _searchQuery, _selectionState, _sortOrder) { songs, query, selection, order ->
        val isPodcast = { song: Song -> song.duration > 600000 && !song.manualNotPodcast }
        
        val baseList = when (selection.view) {
            View.PODCASTS -> songs.filter { isPodcast(it) }
            View.FAVORITES -> songs.filter { it.isFavorite }
            View.RECENTLY_PLAYED -> songs.filter { !isPodcast(it) && it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }
            View.MOST_PLAYED -> songs.filter { !isPodcast(it) && it.playCount > 0 }.sortedByDescending { it.playCount }
            View.RECENTLY_ADDED -> songs.filter { !isPodcast(it) }.sortedByDescending { it.dateAdded }
            View.NEVER_PLAYED -> songs.filter { !isPodcast(it) && it.playCount == 0 }.sortedByDescending { it.dateAdded }
            View.FORGOTTEN_FAVORITES -> songs.filter { it.isFavorite && (System.currentTimeMillis() - it.lastPlayed > 30L * 24 * 60 * 60 * 1000) }.sortedBy { it.lastPlayed }
            View.ARTIST_DETAIL -> songs.filter { !isPodcast(it) && MetadataCleaner.normalizeForComparison(it.artist) == MetadataCleaner.normalizeForComparison(selection.artist ?: "") }
            View.ALBUM_DETAIL -> songs.filter { 
                !isPodcast(it) && 
                MetadataCleaner.normalizeForComparison(it.album) == MetadataCleaner.normalizeForComparison(selection.album ?: "") &&
                (selection.artist == null || MetadataCleaner.normalizeForComparison(it.artist) == MetadataCleaner.normalizeForComparison(selection.artist ?: ""))
            }
            View.GENRE_DETAIL -> songs.filter { !isPodcast(it) && (it.genre ?: "Unknown").equals(selection.genre, ignoreCase = true) }
            View.DECADE_DETAIL -> songs.filter { 
                !isPodcast(it) && selection.decade != null && 
                (if (selection.decade > 0) (it.year >= selection.decade && it.year < selection.decade + 10) else it.year <= 0)
            }
            View.PLAYLIST_DETAIL -> songs.filter { it.path in selection.playlistPaths }
            else -> songs.filter { !isPodcast(it) }
        }

        val sortedList = when (order) {
            SortOrder.TITLE -> baseList.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> baseList.sortedBy { it.artist.lowercase() }
            SortOrder.ALBUM -> baseList.sortedBy { it.album.lowercase() }
            SortOrder.GENRE -> baseList.sortedBy { it.genre?.lowercase() ?: "zzz" }
            SortOrder.DATE_ADDED -> baseList.sortedByDescending { it.dateAdded }
            SortOrder.PLAY_COUNT -> baseList.sortedByDescending { it.playCount }
        }

        if (query.isBlank()) {
            sortedList
        } else {
            sortedList.filter { song ->
                fuzzyMatch(song.title, query) ||
                fuzzyMatch(song.artist, query) ||
                fuzzyMatch(song.album, query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSong = mutableStateOf<Song?>(null)
    val currentSong: State<Song?> = _currentSong

    private val _backImageUrl = mutableStateOf<String?>(null)
    val backImageUrl: State<String?> = _backImageUrl

    private val _currentMediaItemIndex = mutableIntStateOf(0)
    val currentMediaItemIndex: State<Int> = _currentMediaItemIndex

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private val _playbackPosition = mutableLongStateOf(0L)
    val playbackPosition: State<Long> = _playbackPosition

    private val _currentLyrics = mutableStateOf<List<LyricLine>>(emptyList())
    val currentLyrics: State<List<LyricLine>> = _currentLyrics

    private val _showLyrics = mutableStateOf(false)
    val showLyrics: State<Boolean> = _showLyrics

    private val _repeatMode = mutableIntStateOf(Player.REPEAT_MODE_OFF)
    val repeatMode: State<Int> = _repeatMode

    private val _shuffleMode = mutableStateOf(false)
    val shuffleMode: State<Boolean> = _shuffleMode

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _dominantColor = mutableIntStateOf(0xFF1C1B1F.toInt())
    val dominantColor: State<Int> = _dominantColor

    private val _sleepTimerMillis = mutableLongStateOf(0L)
    val sleepTimerMillis: State<Long> = _sleepTimerMillis

    private val _sleepTimerFinishSong = MutableStateFlow(sharedPrefs.getBoolean("sleep_timer_finish_song", false))
    val sleepTimerFinishSong: StateFlow<Boolean> = _sleepTimerFinishSong.asStateFlow()

    private val _crossfadeDuration = MutableStateFlow(sharedPrefs.getInt("crossfade_duration", 2))
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration.asStateFlow()

    private val _limiterEnabled = MutableStateFlow(sharedPrefs.getBoolean("limiter_enabled", false))
    val limiterEnabled: StateFlow<Boolean> = _limiterEnabled.asStateFlow()

    private val _skipSilenceEnabled = MutableStateFlow(sharedPrefs.getBoolean("skip_silence_enabled", false))
    val skipSilenceEnabled: StateFlow<Boolean> = _skipSilenceEnabled.asStateFlow()

    private val _reverbPreset = MutableStateFlow(sharedPrefs.getInt("reverb_preset", 0))
    val reverbPreset: StateFlow<Int> = _reverbPreset.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(sharedPrefs.getFloat("playback_speed", 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(sharedPrefs.getFloat("playback_pitch", 1.0f))
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _gridColumns = MutableStateFlow(sharedPrefs.getInt("grid_columns", 2))
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns
        sharedPrefs.edit().putInt("grid_columns", columns).apply()
    }

    private val _excludedFolders = MutableStateFlow<List<String>>(emptyList())
    val excludedFolders: StateFlow<List<String>> = _excludedFolders.asStateFlow()

    fun addExcludedFolder(path: String) {
        viewModelScope.launch {
            repository.addExcludedFolder(path)
            loadExcludedFolders()
            loadSongs()
        }
    }

    fun removeExcludedFolder(path: String) {
        viewModelScope.launch {
            repository.removeExcludedFolder(path)
            loadExcludedFolders()
            loadSongs()
        }
    }

    private fun loadExcludedFolders() {
        viewModelScope.launch {
            _excludedFolders.value = repository.getExcludedFolders()
        }
    }

    private val _audioSessionId = mutableIntStateOf(0)
    val audioSessionId: State<Int> = _audioSessionId

    private val _visualizerData = mutableStateOf(FloatArray(32))
    val visualizerData: State<FloatArray> = _visualizerData

    private var visualizer: android.media.audiofx.Visualizer? = null

    fun setLimiterEnabled(enabled: Boolean) {
        _limiterEnabled.value = enabled
        sharedPrefs.edit().putBoolean("limiter_enabled", enabled).apply()
        sendEqCommand("SET_LIMITER_ENABLED", Bundle().apply { putBoolean("enabled", enabled) })
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        _skipSilenceEnabled.value = enabled
        sharedPrefs.edit().putBoolean("skip_silence_enabled", enabled).apply()
        sendEqCommand("SET_SKIP_SILENCE_ENABLED", Bundle().apply { putBoolean("enabled", enabled) })
    }

    fun setReverbPreset(preset: Int) {
        _reverbPreset.value = preset
        sharedPrefs.edit().putInt("reverb_preset", preset).apply()
        sendEqCommand("SET_REVERB_PRESET", Bundle().apply { putInt("preset", preset) })
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        sharedPrefs.edit().putFloat("playback_speed", speed).apply()
        sendEqCommand("SET_PLAYBACK_SPEED", Bundle().apply { putFloat("speed", speed) })
    }

    fun setPlaybackPitch(pitch: Float) {
        _playbackPitch.value = pitch
        sharedPrefs.edit().putFloat("playback_pitch", pitch).apply()
        sendEqCommand("SET_PLAYBACK_PITCH", Bundle().apply { putFloat("pitch", pitch) })
    }

    private fun setupVisualizer(sessionId: Int) {
        if (sessionId <= 0) return
        try {
            visualizer?.release()
            visualizer = android.media.audiofx.Visualizer(sessionId).apply {
                captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: android.media.audiofx.Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: android.media.audiofx.Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) {
                            val magnitudes = FloatArray(32)
                            for (i in 0 until 32) {
                                val startBin = i * 2 + 2
                                val endBin = startBin + 2
                                
                                var maxMag = 0f
                                for (j in startBin until endBin) {
                                    if (j * 2 + 1 < fft.size) {
                                        val real = fft[j * 2].toInt()
                                        val imag = fft[j * 2 + 1].toInt()
                                        val mag = Math.sqrt((real * real + imag * imag).toDouble()).toFloat()
                                        if (mag > maxMag) maxMag = mag
                                    }
                                }
                                val divisor = if (i < 4) 40f else if (i < 8) 25f else 16f
                                magnitudes[i] = maxMag / divisor
                            }
                            viewModelScope.launch(Dispatchers.Main) {
                                _visualizerData.value = magnitudes
                            }
                        }
                    }
                }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to setup visualizer", e)
        }
    }

    fun setSleepTimerFinishSong(enabled: Boolean) {
        _sleepTimerFinishSong.value = enabled
        sharedPrefs.edit().putBoolean("sleep_timer_finish_song", enabled).apply()
    }

    fun setCrossfadeDuration(seconds: Int) {
        _crossfadeDuration.value = seconds
        sharedPrefs.edit().putInt("crossfade_duration", seconds).apply()
        sendEqCommand("SET_CROSSFADE", Bundle().apply { putInt("duration", seconds) })
    }

    private val _eqEnabled = MutableStateFlow(sharedPrefs.getBoolean("eq_enabled", false))
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(sharedPrefs.getInt("bass_boost", 0))
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(sharedPrefs.getInt("virtualizer", 0))
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _eqBands = MutableStateFlow(
        (0..9).associateWith { sharedPrefs.getInt("eq_band_$it", 0) }
    )
    val eqBands: StateFlow<Map<Int, Int>> = _eqBands.asStateFlow()

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        sharedPrefs.edit().putBoolean("eq_enabled", enabled).apply()
        sendEqCommand("SET_EQ_ENABLED", Bundle().apply { putBoolean("enabled", enabled) })
    }

    fun setEqBand(band: Int, level: Int) {
        val current = _eqBands.value.toMutableMap()
        current[band] = level
        _eqBands.value = current
        sharedPrefs.edit().putInt("eq_band_$band", level).apply()
        sendEqCommand("SET_EQ_BAND", Bundle().apply {
            putInt("band", band)
            putInt("level", level)
        })
    }

    fun applyEqPreset(preset: Map<Int, Int>) {
        val current = _eqBands.value.toMutableMap()
        preset.forEach { (band, level) ->
            current[band] = level
            sharedPrefs.edit().putInt("eq_band_$band", level).apply()
            sendEqCommand("SET_EQ_BAND", Bundle().apply {
                putInt("band", band)
                putInt("level", level)
            })
        }
        _eqBands.value = current
    }

    fun setBassBoost(strength: Int) {
        _bassBoostStrength.value = strength
        sharedPrefs.edit().putInt("bass_boost", strength).apply()
        sendEqCommand("SET_BASS_BOOST", Bundle().apply { putInt("strength", strength) })
    }

    fun setVirtualizer(strength: Int) {
        _virtualizerStrength.value = strength
        sharedPrefs.edit().putInt("virtualizer", strength).apply()
        sendEqCommand("SET_VIRTUALIZER", Bundle().apply { putInt("strength", strength) })
    }

    private fun sendEqCommand(action: String, args: Bundle) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    enum class SortOrder { TITLE, ARTIST, ALBUM, GENRE, DATE_ADDED, PLAY_COUNT }
    enum class View { SONGS, ARTISTS, ALBUMS, ARTIST_DETAIL, ALBUM_DETAIL, FAVORITES, PLAYLISTS, PLAYLIST_DETAIL, RECENTLY_PLAYED, MOST_PLAYED, RECENTLY_ADDED, NEVER_PLAYED, EQUALIZER, SETTINGS, QUEUE, PODCASTS, GENRES, GENRE_DETAIL, FOLDERS, INSIGHTS, FORGOTTEN_FAVORITES, YT_SEARCH, DECADES, DECADE_DETAIL, ABOUT }
    data class LyricLine(val timeMs: Long, val text: String)

    private val _artistBio = MutableStateFlow<String?>(null)
    val artistBio: StateFlow<String?> = _artistBio.asStateFlow()

    private val _artistDiscography = MutableStateFlow<List<com.steel101.musicplayer.network.AudioDbAlbum>>(emptyList())
    val artistDiscography: StateFlow<List<com.steel101.musicplayer.network.AudioDbAlbum>> = _artistDiscography.asStateFlow()

    private val _mbAlbumTracks = MutableStateFlow<List<com.steel101.musicplayer.network.MBTrack>>(emptyList())
    val mbAlbumTracks: StateFlow<List<com.steel101.musicplayer.network.MBTrack>> = _mbAlbumTracks.asStateFlow()

    private val _isFetchingMbTracks = mutableStateOf(false)
    val isFetchingMbTracks: State<Boolean> = _isFetchingMbTracks

    fun fetchAlbumTracksFromMusicBrainz(albumTitle: String, artistName: String) {
        if (!_isOnlineMode.value) return
        viewModelScope.launch {
            _isFetchingMbTracks.value = true
            _mbAlbumTracks.value = emptyList()
            try {
                val query = "release:\"$albumTitle\" AND artist:\"$artistName\""
                val searchResponse = musicBrainzService.searchRelease(query)
                val release = searchResponse.releases?.firstOrNull()
                if (release != null) {
                    val details = musicBrainzService.getRelease(release.id)
                    val tracks = details.media?.flatMap { it.tracks ?: emptyList() } ?: emptyList()
                    _mbAlbumTracks.value = tracks.sortedBy { it.position }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to fetch MB tracks", e)
            } finally {
                _isFetchingMbTracks.value = false
            }
        }
    }

    private val _isFetchingArtistInfo = mutableStateOf(false)
    val isFetchingArtistInfo: State<Boolean> = _isFetchingArtistInfo

    private val _ytSearchResults = MutableStateFlow<List<StreamInfoItem>>(emptyList())
    val ytSearchResults: StateFlow<List<StreamInfoItem>> = _ytSearchResults.asStateFlow()

    private val _isSearchingYt = mutableStateOf(false)
    val isSearchingYt: State<Boolean> = _isSearchingYt

    fun searchYoutube(query: String) {
        if (query.isBlank() || !_isOnlineMode.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSearchingYt.value = true
            try {
                val youtube = ServiceList.YouTube
                val searchExtractor = youtube.getSearchExtractor(query)
                searchExtractor.fetchPage()
                val results = searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                _ytSearchResults.value = results
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to search YT", e)
                _ytSearchResults.value = emptyList()
            } finally {
                _isSearchingYt.value = false
            }
        }
    }

    val downloadingTracks: StateFlow<Map<String, Float>> = com.steel101.musicplayer.network.DownloadStatus.downloadingTracks

    init {
        viewModelScope.launch {
            com.steel101.musicplayer.network.DownloadStatus.onDownloadFinished.collect { trackKey ->
                if (trackKey != null) {
                    val safeTrackKey = trackKey.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    
                    repository.scanMusicFolder()
                    
                    delay(3000) 
                    loadSongs()
                    
                    val maxRetries = 5
                    var retries = 0
                    var song: Song? = null
                    
                    while (song == null && retries < maxRetries) {
                        song = _songs.value.find { s ->
                            val cleanPath = s.path.substringAfterLast("/")
                            cleanPath.contains(safeTrackKey, ignoreCase = true) ||
                            "${s.artist} - ${s.title}".contains(trackKey, ignoreCase = true)
                        }
                        if (song == null) {
                            delay(2000)
                            loadSongs()
                            retries++
                        }
                    }
                    
                    song?.let { s ->
                        android.util.Log.d("MusicViewModel", "Post-download: Found song ${s.title}, starting enrichment")
                        
                        val cacheFile = File(context.cacheDir, "artwork/$safeTrackKey.jpg")
                        val forcedArt = if (cacheFile.exists()) {
                            android.util.Log.d("MusicViewModel", "Found cached YT thumbnail: ${cacheFile.absolutePath}")
                            cacheFile.absolutePath
                        } else {
                            android.util.Log.w("MusicViewModel", "YT thumbnail NOT found in cache for $safeTrackKey")
                            null
                        }
                        
                        enrichMetadata(s, forcedAlbumArt = forcedArt)
                        
                        delay(1000)
                        loadSongs()
                    }
                }
            }
        }
    }

    fun downloadFromYoutube(trackTitle: String, artistName: String) {
        val intent = Intent(context, com.steel101.musicplayer.network.DownloadService::class.java).apply {
            putExtra("trackTitle", trackTitle)
            putExtra("artistName", artistName)
        }
        context.startForegroundService(intent)
        android.widget.Toast.makeText(context, "Started download: $trackTitle", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun fetchArtistInfo(artistName: String) {
        val cleanName = artistName.trim()
        if (cleanName == "Unknown" || cleanName == "<unknown>") return
        
        viewModelScope.launch {
            if (!_isOnlineMode.value) return@launch
            _isFetchingArtistInfo.value = true
            _artistBio.value = null
            _artistDiscography.value = emptyList()
            try {
                val response = audioDbService.searchArtist(cleanName)
                val artist = response.artists?.firstOrNull()
                if (artist != null) {
                    _artistBio.value = artist.biography
                    val albumsResponse = audioDbService.getAlbums(artist.id!!)
                    _artistDiscography.value = albumsResponse.albums?.sortedByDescending { it.year } ?: emptyList()
                } else {
                    _artistBio.value = "No biography found for this artist."
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to fetch artist info", e)
                _artistBio.value = "Failed to load biography. Please check your connection."
            } finally {
                _isFetchingArtistInfo.value = false
            }
        }
    }

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var positionUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null
    
    private var currentSongStartTimestamp: Long = 0
    private var currentSongPlayTime: Long = 0
    private var hasCountedPlayForCurrentSong = false

    private val musicBrainzService: MusicBrainzService by lazy {
        Retrofit.Builder()
            .baseUrl("https://musicbrainz.org/ws/2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicBrainzService::class.java)
    }

    private val audioDbService: AudioDbService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.theaudiodb.com/api/v1/json/2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AudioDbService::class.java)
    }

    private val itunesService: ItunesService by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ItunesService::class.java)
    }

    private val lyricsService: LyricsService by lazy {
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LyricsService::class.java)
    }

    private val neteaseService: NeteaseService by lazy {
        Retrofit.Builder()
            .baseUrl("https://music.163.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeteaseService::class.java)
    }

    private val acoustIdService: AcoustIdService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.acoustid.org/v2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AcoustIdService::class.java)
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            restorePlaybackState()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        currentSongStartTimestamp = System.currentTimeMillis()
                        startPositionUpdates()
                    } else {
                        stopPositionUpdates()
                        updateTotalPlayTime()
                        savePlaybackState()
                    }
                }

                override fun onRepeatModeChanged(repeatMode: Int) { _repeatMode.intValue = repeatMode }
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { _shuffleMode.value = shuffleModeEnabled }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    _currentMediaItemIndex.intValue = controller?.currentMediaItemIndex ?: 0
                    updateQueue()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _currentMediaItemIndex.intValue = controller?.currentMediaItemIndex ?: 0
                    
                    updateTotalPlayTime()
                    
                    val currentMediaId = mediaItem?.mediaId
                    val song = _songs.value.find { it.id.toString() == currentMediaId }
                    
                    currentSongPlayTime = 0
                    hasCountedPlayForCurrentSong = false
                    currentSongStartTimestamp = if (_isPlaying.value) System.currentTimeMillis() else 0
                    
                    _currentSong.value = song
                    _backImageUrl.value = song?.backImageUrl
                    _currentLyrics.value = emptyList()
                    if (song != null) {
                        fetchLyrics(song)
                        updateDominantColor(context, song)
                        savePlaybackState()
                    }
                    
                    viewModelScope.launch {
                        delay(1000) 
                        val commandFuture = controller?.sendCustomCommand(SessionCommand("GET_AUDIO_SESSION_ID", Bundle.EMPTY), Bundle.EMPTY)
                        commandFuture?.addListener({
                                try {
                                    val result = commandFuture.get()
                                    val sessionId = result.extras.getInt("audio_session_id")
                                    if (sessionId != 0 && sessionId != _audioSessionId.intValue) {
                                        _audioSessionId.intValue = sessionId
                                        setupVisualizer(sessionId)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MusicViewModel", "Failed to get session ID", e)
                                }
                            }, MoreExecutors.directExecutor())
                    }
                }
            })

            setEqEnabled(_eqEnabled.value)
            _eqBands.value.forEach { (band, level) -> setEqBand(band, level) }
            setBassBoost(_bassBoostStrength.value)
            setVirtualizer(_virtualizerStrength.value)
            setCrossfadeDuration(_crossfadeDuration.value)
            setLimiterEnabled(_limiterEnabled.value)
            setSkipSilenceEnabled(_skipSilenceEnabled.value)
            setReverbPreset(_reverbPreset.value)
            setPlaybackSpeed(_playbackSpeed.value)
            setPlaybackPitch(_playbackPitch.value)

            viewModelScope.launch {
                delay(1000)
                val commandFuture = controller?.sendCustomCommand(SessionCommand("GET_AUDIO_SESSION_ID", Bundle.EMPTY), Bundle.EMPTY)
                commandFuture?.addListener({
                        try {
                            val result = commandFuture.get()
                            val sessionId = result.extras.getInt("audio_session_id")
                            if (sessionId != 0) {
                                _audioSessionId.intValue = sessionId
                                setupVisualizer(sessionId)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MusicViewModel", "Failed to get initial session ID", e)
                        }
                    }, MoreExecutors.directExecutor())
            }
        }, MoreExecutors.directExecutor())

        loadSongs()
        loadPlaylists()
        loadExcludedFolders()
    }

    private fun updateTotalPlayTime() {
        val song = _currentSong.value ?: return
        if (currentSongStartTimestamp > 0 && _isPlaying.value) {
            val now = System.currentTimeMillis()
            val sessionTime = now - currentSongStartTimestamp
            currentSongPlayTime += sessionTime
            currentSongStartTimestamp = now
            
            viewModelScope.launch {
                repository.addPlayTime(song, sessionTime)
                _songs.value = _songs.value.map {
                    if (it.id == song.id) it.copy(totalPlayTimeMs = it.totalPlayTimeMs + sessionTime) else it
                }
            }
        }
    }

    private fun checkPlayCount(song: Song, positionMs: Long) {
        if (!hasCountedPlayForCurrentSong && song.duration > 0) {
            val progress = positionMs.toFloat() / song.duration
            if (progress >= 0.75f) {
                hasCountedPlayForCurrentSong = true
                recordPlay(song)
            }
        }
    }

    private fun updateDominantColor(context: Context, song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val albumArtUri = if (song.hasEmbeddedArt) {
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                } else {
                    song.albumImageUrl?.let { Uri.parse(it) }
                }

                if (albumArtUri != null) {
                    val inputStream = context.contentResolver.openInputStream(albumArtUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        Palette.from(bitmap).generate { palette ->
                            val color = palette?.getDarkVibrantColor(0xFF1C1B1F.toInt())
                                ?: palette?.getDominantColor(0xFF1C1B1F.toInt())
                                ?: 0xFF1C1B1F.toInt()
                            _dominantColor.intValue = color
                        }
                    }
                } else {
                    _dominantColor.intValue = 0xFF1C1B1F.toInt()
                }
            } catch (e: Exception) {
                _dominantColor.intValue = 0xFF1C1B1F.toInt()
            }
        }
    }

    private fun recordPlay(song: Song) {
        viewModelScope.launch {
            repository.recordPlay(song)
            _songs.value = _songs.value.map {
                if (it.id == song.id) it.copy(playCount = it.playCount + 1, lastPlayed = System.currentTimeMillis()) else it
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMillis.longValue = minutes * 60 * 1000L
        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerMillis.longValue > 0) {
                delay(1000)
                _sleepTimerMillis.longValue -= 1000
            }
            if (_sleepTimerFinishSong.value) {
                while (controller?.isPlaying == true) {
                    delay(1000)
                }
            }
            stop()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerMillis.longValue = 0
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
            val updatedSong = song.copy(isFavorite = !song.isFavorite)
            _songs.value = _songs.value.map { if (it.id == song.id) updatedSong else it }
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = updatedSong
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            _playlists.value = repository.getPlaylists()
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
            loadPlaylists()
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, song.path)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            loadPlaylists()
            if (_selectionState.value.playlist?.id == playlist.id) {
                setView(View.PLAYLISTS)
            }
        }
    }

    fun setView(view: View, artist: String? = null, album: String? = null, playlist: PlaylistEntity? = null, genre: String? = null, decade: Int? = null) {
        viewModelScope.launch {
            val paths = if (view == View.PLAYLIST_DETAIL && playlist != null) {
                repository.getSongsInPlaylist(playlist.id)
            } else {
                emptyList()
            }
            _selectionState.value = SelectionState(view, artist, album, playlist, genre, decade, paths)
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            var updateCount = 0
            while (true) {
                val pos = controller?.currentPosition ?: 0L
                _playbackPosition.longValue = pos
                
                _currentSong.value?.let { checkPlayCount(it, pos) }
                
                updateCount++
                if (updateCount >= 50) {
                    updateCount = 0
                    updateTotalPlayTime()
                    savePlaybackState()
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
    }

    private fun savePlaybackState() {
        val current = _currentSong.value ?: return
        val currentQueue = _songs.value
        val queueIds = currentQueue.map { it.id }.joinToString(",")
        val position = controller?.currentPosition ?: 0L
        
        sharedPrefs.edit()
            .putLong("last_song_id", current.id)
            .putString("last_queue_ids", queueIds)
            .putLong("last_playback_position", position)
            .apply()
    }

    private fun restorePlaybackState() {
        val lastSongId = sharedPrefs.getLong("last_song_id", -1L)
        if (lastSongId == -1L) return
        
        val lastPosition = sharedPrefs.getLong("last_playback_position", 0L)
        val lastQueueStr = sharedPrefs.getString("last_queue_ids", "") ?: ""
        
        viewModelScope.launch {
            try {
                val songList = _songs.first { it.isNotEmpty() }
                if (controller != null && controller?.currentMediaItem == null) {
                    val queueIds = lastQueueStr.split(",").mapNotNull { it.toLongOrNull() }
                    val restoredQueue = if (queueIds.isNotEmpty()) {
                        queueIds.mapNotNull { id -> songList.find { it.id == id } }
                    } else {
                        songList
                    }
                    
                    if (restoredQueue.isNotEmpty()) {
                        val targetSong = restoredQueue.find { it.id == lastSongId } ?: restoredQueue.first()
                        val startIndex = restoredQueue.indexOfFirst { it.id == targetSong.id }.coerceAtLeast(0)
                        
                        val mediaItems = restoredQueue.map { item ->
                            val albumArtUri = if (item.hasEmbeddedArt) {
                                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), item.albumId)
                            } else {
                                item.albumImageUrl?.let { Uri.parse(it) }
                            }

                            val metadata = MediaMetadata.Builder()
                                .setTitle(item.title)
                                .setArtist(item.artist)
                                .setAlbumTitle(item.album)
                                .setArtworkUri(albumArtUri)
                                .build()

                            MediaItem.Builder()
                                .setMediaId(item.id.toString())
                                .setUri(item.uri)
                                .setMediaMetadata(metadata)
                                .build()
                        }
                        
                        controller?.setMediaItems(mediaItems, startIndex, lastPosition)
                        controller?.prepare()
                        
                        _currentSong.value = targetSong
                        _playbackPosition.longValue = lastPosition
                        updateDominantColor(context, targetSong)
                        fetchLyrics(targetSong)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to restore state", e)
            }
        }
    }

    private fun fetchLyrics(song: Song) {
        viewModelScope.launch {
            if (!_isOnlineMode.value) return@launch
            try {
                val localFile = File(context.filesDir, "lyrics_${song.id}.lrc")
                if (localFile.exists()) {
                    val lrcText = localFile.readText()
                    _currentLyrics.value = parseLrc(lrcText)
                    return@launch
                }

                var lrcText: String? = null
                var isSynced = false
                val cleanAlbum = if (song.album == "Unknown" || song.album.contains(".mp3", ignoreCase = true)) null else song.album
                
                try {
                    val response = lyricsService.getLyrics(
                        song.title, 
                        song.artist, 
                        cleanAlbum, 
                        (song.duration / 1000).toInt(),
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                    )
                    lrcText = response.syncedLyrics ?: response.plainLyrics
                    isSynced = response.syncedLyrics != null
                } catch (e: Exception) {
                    val searchResponse = lyricsService.searchLyrics(
                        "${song.artist} ${song.title}",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                    )
                    val match = searchResponse.firstOrNull { it.syncedLyrics != null || it.plainLyrics != null }
                    lrcText = match?.syncedLyrics ?: match?.plainLyrics
                    isSynced = match?.syncedLyrics != null
                }

                if (lrcText == null || !isSynced) {
                    try {
                        val searchResponse = neteaseService.search("${song.artist} ${song.title}")
                        val neteaseSong = searchResponse.result?.songs?.firstOrNull()
                        if (neteaseSong != null) {
                            val lyricResponse = neteaseService.getLyrics(neteaseSong.id)
                            val neteaseLrc = lyricResponse.lrc?.lyric
                            if (neteaseLrc != null) {
                                val netEaseIsSynced = neteaseLrc.contains(Regex("""\[\d{2}:\d{2}"""))
                                if (netEaseIsSynced || lrcText == null) {
                                    lrcText = neteaseLrc
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
                
                if (lrcText != null) {
                    _currentLyrics.value = parseLrc(lrcText)
                }
            } catch (e: Exception) {
                _currentLyrics.value = emptyList()
            }
        }
    }

    fun saveSyncedLyrics(song: Song, lrcText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localFile = File(context.filesDir, "lyrics_${song.id}.lrc")
                localFile.writeText(lrcText)
                withContext(Dispatchers.Main) {
                    _currentLyrics.value = parseLrc(lrcText)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to save synced lyrics", e)
            }
        }
    }

    fun deleteSyncedLyrics(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localFile = File(context.filesDir, "lyrics_${song.id}.lrc")
                if (localFile.exists()) {
                    localFile.delete()
                }
                withContext(Dispatchers.Main) {
                    fetchLyrics(song)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to delete synced lyrics", e)
            }
        }
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})[.:](\d{2,3})\](.*)""")
        
        lrc.split("\n").forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msStr = match.groupValues[3]
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val time = (min * 60 * 1000) + (sec * 1000) + ms
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(time, text))
                }
            }
        }
        
        if (lines.isEmpty() && lrc.isNotBlank()) {
            return lrc.split("\n")
                .filter { it.trim().isNotEmpty() && !it.startsWith("[") }
                .map { LyricLine(0, it.trim()) }
        }
        
        return lines.sortedBy { it.timeMs }
    }

    fun toggleLyrics() { _showLyrics.value = !_showLyrics.value }

    fun loadSongs() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val fetchedSongs = repository.getSongs()
                _songs.value = fetchedSongs.filter { !it.isHidden }
                
                enrichmentJob?.cancel()
                enrichmentJob = viewModelScope.launch {
                    if (_isAutoTagEnabled.value) {
                        fetchedSongs.filter { song ->
                            !song.hasEmbeddedArt && song.albumImageUrl != null
                        }.forEach { song ->
                            performTagWrite(song)
                            delay(500)
                        }
                    }

                    fetchedSongs.filter { song ->
                        if (song.manualOverride) return@filter false
                        val needsAlbumArt = !song.hasEmbeddedArt && song.albumImageUrl == null
                        val needsArtistArt = song.artistImageUrl == null
                        val needsYear = song.year == 0
                        val needsGenre = song.genre == null
                        val hasBasicInfo = song.artist != "Unknown" && song.artist != "Unknown Artist"
                        (needsAlbumArt || needsArtistArt || needsYear || needsGenre) && hasBasicInfo
                    }.forEach { song ->
                        enrichMetadata(song)
                        delay(1100)
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun manualRescan() {
        viewModelScope.launch {
            repository.scanMusicFolder()
            delay(2000)
            loadSongs()
        }
    }

    private val _gaplessPlaybackEnabled = MutableStateFlow(sharedPrefs.getBoolean("gapless_playback", true))
    val gaplessPlaybackEnabled: StateFlow<Boolean> = _gaplessPlaybackEnabled.asStateFlow()

    fun setGaplessPlaybackEnabled(enabled: Boolean) {
        _gaplessPlaybackEnabled.value = enabled
        sharedPrefs.edit().putBoolean("gapless_playback", enabled).apply()
        sendEqCommand("SET_GAPLESS_ENABLED", Bundle().apply { putBoolean("enabled", enabled) })
    }

    fun playPreview(title: String, artist: String) {
        if (!_isOnlineMode.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val youtube = ServiceList.YouTube
                val searchExtractor = youtube.getSearchExtractor("$artist - $title")
                searchExtractor.fetchPage()
                val videoItem = searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().firstOrNull()
                if (videoItem != null) {
                    playYoutubePreview(videoItem)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to play preview for $title", e)
            }
        }
    }

    fun playYoutubePreview(item: StreamInfoItem) {
        if (!_isOnlineMode.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val youtube = ServiceList.YouTube
                val streamExtractor = youtube.getStreamExtractor(item.url)
                streamExtractor.fetchPage()
                
                val audioStream = streamExtractor.audioStreams
                    .filter { it.format?.suffix?.contains("m4a") == true }
                    .maxByOrNull { it.bitrate }
                    ?: streamExtractor.audioStreams.maxByOrNull { it.bitrate }

                val streamUrl = audioStream?.url
                if (streamUrl != null) {
                    withContext(Dispatchers.Main) {
                        val metadata = MediaMetadata.Builder()
                            .setTitle(item.name)
                            .setArtist(item.uploaderName)
                            .setArtworkUri(item.thumbnails?.firstOrNull()?.url?.let { Uri.parse(it) })
                            .build()

                        val mediaItem = MediaItem.Builder()
                            .setMediaId("yt_preview_${item.url.hashCode()}")
                            .setUri(Uri.parse(streamUrl))
                            .setMediaMetadata(metadata)
                            .build()

                        controller?.setMediaItem(mediaItem)
                        controller?.prepare()
                        controller?.play()
                        
                        _currentSong.value = Song(
                            id = -1,
                            title = item.name,
                            artist = item.uploaderName ?: "Unknown",
                            album = "YouTube Preview",
                            duration = item.duration * 1000L,
                            uri = Uri.parse(streamUrl),
                            albumId = -1,
                            trackNumber = 0,
                            year = 0,
                            path = "",
                            filename = "",
                            albumImageUrl = item.thumbnails?.firstOrNull()?.url
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to play preview", e)
            }
        }
    }

    suspend fun enrichMetadata(song: Song, forcedAlbumArt: String? = null) {
        if (!_isOnlineMode.value && forcedAlbumArt == null) return
        try {
            val cleanTitle = MetadataCleaner.cleanString(song.title)
            val cleanArtist = MetadataCleaner.cleanString(song.artist)
            
            var title = song.title
            var artist = song.artist
            var album = song.album
            var year = song.year
            var genre = song.genre
            var artistMbid = song.artistMbid
            var albumMbid = song.albumMbid
            var albumImageUrl = forcedAlbumArt ?: song.albumImageUrl
            var artistImageUrl = song.artistImageUrl
            var backImageUrl = song.backImageUrl

            try {
                var query = "recording:\"$cleanTitle\" AND artist:\"$cleanArtist\""
                var response = musicBrainzService.searchRecording(query)
                var recording = response.recordings?.find { res ->
                    val resTitle = MetadataCleaner.normalizeForComparison(res.title ?: "")
                    val targetTitle = MetadataCleaner.normalizeForComparison(cleanTitle)
                    resTitle.contains(targetTitle) || targetTitle.contains(resTitle)
                } ?: response.recordings?.firstOrNull()

                if (recording == null) {
                    query = "recording:\"$cleanTitle\""
                    response = musicBrainzService.searchRecording(query)
                    recording = response.recordings?.find { res ->
                        val resArtist = res.artistCredit?.firstOrNull()?.artist?.name ?: ""
                        resArtist.contains(cleanArtist, ignoreCase = true) || cleanArtist.contains(resArtist, ignoreCase = true)
                    }
                }

                if (recording != null) {
                    if (title.contains("unknown", ignoreCase = true) || title.contains(".mp3", ignoreCase = true)) {
                        title = recording.title
                    }
                    if (artist.contains("unknown", ignoreCase = true)) {
                        artist = recording.artistCredit?.firstOrNull()?.artist?.name ?: artist
                    }
                    
                    artistMbid = recording.artistCredit?.firstOrNull()?.artist?.id
                    
                    if (genre == null) {
                        genre = recording.tags?.maxByOrNull { it.count }?.name?.replaceFirstChar { it.uppercase() }
                    }

                    val release = recording.releases?.firstOrNull()
                    if (release != null) {
                        if (album.contains("unknown", ignoreCase = true)) {
                            album = release.title
                        }
                        albumMbid = release.id
                        year = release.date?.take(4)?.toIntOrNull() ?: year
                        
                        if (albumImageUrl == null) {
                            albumImageUrl = "https://coverartarchive.org/release/$albumMbid/front"
                            backImageUrl = "https://coverartarchive.org/release/$albumMbid/back"
                        }
                    }
                }
            } catch (e: Exception) {}

            if (albumImageUrl == null || genre == null || year == 0) {
                try {
                    val itunesResponse = itunesService.search("$cleanArtist $cleanTitle", entity = "musicTrack", limit = 1)
                    val result = itunesResponse.results?.firstOrNull()
                    if (result != null) {
                        if (albumImageUrl == null) albumImageUrl = result.artworkUrl100?.replace("100x100bb", "1000x1000bb")
                        if (genre == null) genre = result.primaryGenreName
                        if (year == 0) year = result.releaseDate?.take(4)?.toIntOrNull() ?: 0
                        if (album.contains("unknown", ignoreCase = true)) {
                            album = result.collectionName ?: album
                        }
                    }
                } catch (e: Exception) {}
            }

            if (artistImageUrl == null) {
                try {
                    val response = audioDbService.searchArtist(cleanArtist)
                    val artistInfo = response.artists?.firstOrNull { 
                        it.name?.lowercase() == cleanArtist.lowercase() 
                    } ?: response.artists?.firstOrNull()
                    
                    artistImageUrl = artistInfo?.thumbUrl ?: artistInfo?.fanartUrl
                } catch (e: Exception) {}

                if (artistImageUrl == null) {
                    try {
                        val itunesResponse = itunesService.search(cleanArtist, entity = "musicTrack", limit = 1)
                        val result = itunesResponse.results?.firstOrNull()
                        if (result != null) {
                            artistImageUrl = result.artworkUrl100?.replace("100x100bb", "1000x1000bb")
                        }
                    } catch (e: Exception) {}
                }
            }

            val hasChanged = title != song.title || artist != song.artist || album != song.album || 
                             year != song.year || genre != song.genre || artistMbid != song.artistMbid || 
                             albumMbid != song.albumMbid || artistImageUrl != song.artistImageUrl || 
                             albumImageUrl != song.albumImageUrl || backImageUrl != song.backImageUrl ||
                             forcedAlbumArt != null

            if (hasChanged) {
                val updatedSong = song.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    year = year,
                    genre = genre,
                    artistMbid = artistMbid,
                    albumMbid = albumMbid,
                    albumImageUrl = albumImageUrl,
                    artistImageUrl = artistImageUrl,
                    backImageUrl = backImageUrl,
                    manualOverride = forcedAlbumArt != null || song.manualOverride
                )
                repository.saveMetadata(updatedSong, isManual = forcedAlbumArt != null)
                
                _songs.value = _songs.value.map { if (it.id == song.id) updatedSong else it }

                if (_isAutoTagEnabled.value || forcedAlbumArt != null) {
                    performTagWrite(updatedSong)
                }
                
                if (_currentSong.value?.id == song.id) {
                    _currentSong.value = updatedSong
                    _backImageUrl.value = updatedSong.backImageUrl
                }
            }
        } catch (e: Exception) {}
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            loadSongs()
        }
    }

    fun batchFixMetadata() {
        enrichmentJob?.cancel()
        enrichmentJob = viewModelScope.launch {
            _songs.value.filter { song ->
                !song.manualOverride && (song.albumImageUrl == null || song.artistImageUrl == null)
            }.forEach { song ->
                enrichMetadata(song)
                delay(1100)
            }
        }
    }

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _isSearching = mutableStateOf(false)
    val isSearching: State<Boolean> = _isSearching

    fun searchMetadata(title: String, artist: String) {
        if (!_isOnlineMode.value) return
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val query = "recording:\"$title\" AND artist:\"$artist\""
                val response = musicBrainzService.searchRecording(query)
                val results = response.recordings?.map { res ->
                    val artistMbid = res.artistCredit?.firstOrNull()?.artist?.id
                    val albumMbid = res.releases?.firstOrNull()?.id
                    val albumImageUrl = if (albumMbid != null) "https://coverartarchive.org/release/$albumMbid/front" else null
                    Song(0, res.title ?: "Unknown", res.artistCredit?.firstOrNull()?.artist?.name ?: "Unknown", res.releases?.firstOrNull()?.title ?: "Unknown", 0, Uri.EMPTY, 0, artistMbid = artistMbid, albumMbid = albumMbid, albumImageUrl = albumImageUrl)
                } ?: emptyList()
                _searchResults.value = results
            } catch (e: Exception) {} finally { _isSearching.value = false }
        }
    }

    fun deleteSong(song: Song) {
        songToDelete = song
        viewModelScope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                    _pendingDeleteRequest.value = pendingIntent
                } else {
                    context.contentResolver.delete(song.uri, null, null)
                    repository.metadataDao.deleteMetadata(song.path)
                    loadSongs()
                }
            } catch (e: Exception) {
                val rse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    e as? android.app.RecoverableSecurityException
                        ?: e.cause as? android.app.RecoverableSecurityException
                } else null
                
                if (rse != null) {
                    _pendingDeleteRequest.value = rse.userAction.actionIntent
                } else {
                    android.util.Log.e("MusicViewModel", "Failed to delete song", e)
                }
            }
        }
    }

    fun onSongDeleted() {
        songToDelete?.let { song ->
            viewModelScope.launch(Dispatchers.IO) {
                repository.metadataDao.deleteMetadata(song.path)
                withContext(Dispatchers.Main) {
                    loadSongs()
                    songToDelete = null
                }
            }
        }
    }

    private fun performTagWrite(song: Song) {
        viewModelScope.launch {
            try {
                repository.writeTagsToFile(song)
                repository.renameFileToMatchMetadata(song)?.let {
                    loadSongs()
                }
            } catch (e: Exception) {
                val isScopedStorageError = e is SecurityException || 
                                         e.cause is SecurityException

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isScopedStorageError) {
                    lastFailedSong = song
                    val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri))
                    _pendingWriteRequest.value = pendingIntent
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val recoverableSecurityException = e as? android.app.RecoverableSecurityException
                        ?: e.cause as? android.app.RecoverableSecurityException
                    if (recoverableSecurityException != null) {
                        lastFailedSong = song
                        _pendingWriteRequest.value = recoverableSecurityException.userAction.actionIntent
                    }
                }
            }
        }
    }

    fun applyManualMetadata(originalSong: Song, selectedMetadata: Song) {
        viewModelScope.launch {
            var artistImageUrl: String? = null
            if (_isOnlineMode.value) {
                try {
                    val response = audioDbService.searchArtist(selectedMetadata.artist)
                    val artist = response.artists?.firstOrNull { it.name?.lowercase() == selectedMetadata.artist.lowercase() }
                        ?: response.artists?.firstOrNull()
                    artistImageUrl = artist?.thumbUrl ?: artist?.fanartUrl
                } catch (e: Exception) {}
            }

            val updatedSong = originalSong.copy(
                title = selectedMetadata.title,
                artist = selectedMetadata.artist,
                album = selectedMetadata.album,
                artistMbid = selectedMetadata.artistMbid,
                albumMbid = selectedMetadata.albumMbid,
                albumImageUrl = selectedMetadata.albumImageUrl,
                artistImageUrl = artistImageUrl,
                hasEmbeddedArt = false,
                manualOverride = true
            )
            repository.saveMetadata(updatedSong, isManual = true)
            performTagWrite(updatedSong)
            _songs.value = _songs.value.map { if (it.id == originalSong.id) updatedSong else it }
            
            loadSongs()
            
            if (_currentSong.value?.id == originalSong.id) {
                _currentSong.value = updatedSong
                fetchLyrics(updatedSong)
            }
        }
    }

    fun saveManualMetadata(originalSong: Song, newTitle: String, newArtist: String) {
        viewModelScope.launch {
            val updatedSong = originalSong.copy(
                title = newTitle,
                artist = newArtist,
                manualOverride = true
            )
            repository.saveMetadata(updatedSong, isManual = true)
            performTagWrite(updatedSong)
            _songs.value = _songs.value.map { if (it.id == originalSong.id) updatedSong else it }
            
            loadSongs()
            
            if (_currentSong.value?.id == originalSong.id) {
                _currentSong.value = updatedSong
                fetchLyrics(updatedSong)
            }
        }
    }

    fun saveGenre(song: Song, newGenre: String) {
        viewModelScope.launch {
            val updatedSong = song.copy(
                genre = newGenre,
                manualOverride = true
            )
            repository.saveMetadata(updatedSong, isManual = true)
            performTagWrite(updatedSong)
            _songs.value = _songs.value.map { if (it.id == song.id) updatedSong else it }
            
            loadSongs()
            
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = updatedSong
            }
        }
    }

    fun hideSong(song: Song) {
        viewModelScope.launch {
            repository.toggleHideSong(song)
            loadSongs()
        }
    }

    fun toggleNotPodcast(song: Song) {
        viewModelScope.launch {
            repository.setManualNotPodcast(song, !song.manualNotPodcast)
            loadSongs()
        }
    }

    private fun saveLocalImageToCache(uriStr: String, prefix: String): String {
        if (!uriStr.startsWith("content://")) return uriStr
        return try {
            val uri = Uri.parse(uriStr)
            val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.toURI().toString()
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to copy local image to cache", e)
            uriStr
        }
    }

    fun updateArtistImage(artistName: String, imageUrl: String) {
        viewModelScope.launch {
            val persistentUrl = saveLocalImageToCache(imageUrl, "artist_${artistName.replace(" ", "_")}")
            repository.updateArtistImage(artistName, persistentUrl)
            loadSongs()
        }
    }

    fun updateSongArtwork(song: Song, imageUrl: String) {
        viewModelScope.launch {
            val persistentUrl = saveLocalImageToCache(imageUrl, "song_${song.id}")
            val updatedSong = song.copy(
                albumImageUrl = persistentUrl,
                hasEmbeddedArt = false,
                manualOverride = true
            )
            repository.saveMetadata(updatedSong, isManual = true)
            performTagWrite(updatedSong)
            _songs.value = _songs.value.map { if (it.id == song.id) updatedSong else it }
            
            loadSongs()
            
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = updatedSong
            }
        }
    }

    fun updateAlbumArtwork(album: AlbumInfo, imageUrl: String) {
        viewModelScope.launch {
            val persistentUrl = saveLocalImageToCache(imageUrl, "album_${album.title.replace(" ", "_")}")
            album.songs.forEach { song ->
                val updatedSong = song.copy(
                    albumImageUrl = persistentUrl,
                    hasEmbeddedArt = false,
                    manualOverride = true
                )
                repository.saveMetadata(updatedSong, isManual = true)
                performTagWrite(updatedSong)
            }
            loadSongs()
        }
    }

    private fun updateQueue() {
        controller?.let {
            val items = mutableListOf<Song>()
            for (i in 0 until it.mediaItemCount) {
                val mediaItem = it.getMediaItemAt(i)
                val song = _songs.value.find { s -> s.id.toString() == mediaItem.mediaId }
                if (song != null) {
                    items.add(song)
                }
            }
            _currentQueue.value = items
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun playSong(song: Song, fromList: List<Song>? = null) {
        val songList = fromList ?: _songs.value
        if (song.uri == Uri.EMPTY) return
        
        controller?.let { player ->
            if (player.currentMediaItem?.mediaId == song.id.toString() && player.mediaItemCount == songList.size) {
                if (!player.isPlaying) {
                    player.play()
                }
                return
            }

            val mediaItems = songList.map { it.toMediaItem() }
            val startIndex = songList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            
            player.stop()
            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        
        controller?.let { player ->
            val mediaItems = songs.map { it.toMediaItem() }
            
            val actualStartIndex = if (shuffle) {
                (0 until mediaItems.size).random()
            } else {
                startIndex
            }

            player.stop()
            player.shuffleModeEnabled = shuffle
            player.setMediaItems(mediaItems, actualStartIndex, 0L)
            player.prepare()
            player.play()
        }
    }

    fun playNext(song: Song) {
        controller?.let {
            val index = it.currentMediaItemIndex + 1
            it.addMediaItem(index, song.toMediaItem())
        }
    }

    fun addToQueue(song: Song) {
        controller?.addMediaItem(song.toMediaItem())
    }

    private fun Song.toMediaItem(): MediaItem {
        val albumArtUri = if (this.hasEmbeddedArt) {
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), this.albumId)
        } else {
            this.albumImageUrl?.let { Uri.parse(it) }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(this.title)
            .setArtist(this.artist)
            .setAlbumTitle(this.album)
            .setArtworkUri(albumArtUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(this.id.toString())
            .setUri(this.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    fun skipNext() { controller?.seekToNext() }
    fun skipPrevious() { controller?.seekToPrevious() }
    fun toggleRepeatMode() {
        controller?.repeatMode = when (_repeatMode.intValue) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun toggleShuffleMode() { controller?.shuffleModeEnabled = !_shuffleMode.value }
    fun togglePlayPause() {
        if (controller?.isPlaying == true) controller?.pause() else { controller?.volume = 1.0f; controller?.play() }
    }
    fun stop() { controller?.stop() }

    fun identifySong(song: Song) {
        if (!_isOnlineMode.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
                val path = song.path
                if (path.isEmpty() || !File(path).exists()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Audio file not found for fingerprinting", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val result = FpCalc.get(context).makeFingerprint(arrayOf("-length", "120", path))
                
                var fingerprint: String? = null
                var duration: Int? = null
                
                result.split("\n").forEach { line ->
                    if (line.startsWith("FINGERPRINT=")) {
                        fingerprint = line.substringAfter("FINGERPRINT=")
                    } else if (line.startsWith("DURATION=")) {
                        duration = line.substringAfter("DURATION=").toIntOrNull()
                    }
                }

                android.util.Log.d("MusicViewModel", "Fingerprint length: ${fingerprint?.length ?: 0}, Duration: $duration")

                if (fingerprint != null && duration != null) {
                    android.util.Log.d("MusicViewModel", "Sending AcoustID lookup...")
                    val response = acoustIdService.lookup(
                        client = com.steel101.musicplayer.BuildConfig.ACOUSTID_KEY,
                        duration = duration!!,
                        fingerprint = fingerprint!!
                    )
                    
                    android.util.Log.d("MusicViewModel", "AcoustID Response Status: ${response.status}")

                    if (response.status != "ok") {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "AcoustID API Error: ${response.status}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    val bestResult = response.results?.maxByOrNull { it.score }
                    val bestRecording = bestResult?.recordings?.firstOrNull()

                    if (bestRecording != null) {
                        android.util.Log.d("MusicViewModel", "AcoustID found recording: ${bestRecording.id}, searching MusicBrainz...")
                        val mbResponse = musicBrainzService.searchRecording("rid:${bestRecording.id}")
                        val mbMatch = mbResponse.recordings?.firstOrNull()

                        val identified = song.copy(
                            title = mbMatch?.title ?: bestRecording.title ?: song.title,
                            artist = mbMatch?.artistCredit?.firstOrNull()?.artist?.name 
                                     ?: bestRecording.artists?.firstOrNull()?.name ?: song.artist,
                            album = mbMatch?.releases?.firstOrNull()?.title ?: song.album,
                            year = mbMatch?.releases?.firstOrNull()?.date?.take(4)?.toIntOrNull() ?: song.year,
                            artistMbid = mbMatch?.artistCredit?.firstOrNull()?.artist?.id 
                                         ?: bestRecording.artists?.firstOrNull()?.id,
                            albumMbid = mbMatch?.releases?.firstOrNull()?.id,
                            manualOverride = true
                        )
                        
                        repository.saveMetadata(identified, isManual = true)
                        performTagWrite(identified)
                        
                        withContext(Dispatchers.Main) {
                            loadSongs()
                            android.widget.Toast.makeText(context, "AcoustID Matched: ${identified.title}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Fingerprint not found in database", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Identify failed", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error during fingerprinting", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
        visualizer?.release()
        controller?.release()
        MediaController.releaseFuture(controllerFuture)
    }
}
