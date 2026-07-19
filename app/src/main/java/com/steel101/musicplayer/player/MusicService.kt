package com.steel101.musicplayer.player

import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import com.steel101.musicplayer.widget.MusicWidgetProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.PresetReverb
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.steel101.musicplayer.MusicApplication
import com.steel101.musicplayer.data.Song

class MusicService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var exoPlayer: ExoPlayer

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var volumeJob: kotlinx.coroutines.Job? = null

    private var isEqEnabled = false
    private val eqBands = mutableMapOf<Int, Int>()
    private var bassBoostStrength: Int = 0
    private var virtualizerStrength: Int = 0
    private var limiterEnabled = false
    private var skipSilenceEnabled = false
    private var reverbPreset: Int = 0
    private var isReplayGainEnabled = false
    private var currentTrackGain: Float = 0f

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var presetReverb: PresetReverb? = null

    @OptIn(UnstableApi::class)
    private fun initAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        dynamicsProcessing?.release()
        presetReverb?.release()

        try {
            val limiter = DynamicsProcessing.Limiter(
                true,
                true,
                0,
                1.0f,
                50.0f,
                1.0f,
                -1.0f,
                0.0f
            )
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                1,
                false, 0,
                false, 0,
                false, 0,
                true
            ).setPreferredFrameDuration(10.0f)
            .build()
            
            equalizer = Equalizer(100, audioSessionId)
            bassBoost = BassBoost(100, audioSessionId)
            virtualizer = Virtualizer(100, audioSessionId)
            presetReverb = PresetReverb(100, audioSessionId)
            
            dynamicsProcessing = DynamicsProcessing(100, audioSessionId, config)
            try {
                dynamicsProcessing?.setLimiterAllChannelsTo(limiter)
            } catch (e: Exception) { Log.e("MusicService", "Limiter config failed", e) }
            
            applyAllSettings()
            
            Log.d("MusicService", "Audio effects re-initialized with high priority for session: $audioSessionId")
        } catch (e: Exception) {
            Log.e("MusicService", "Failed to initialize audio effects", e)
        }
    }

    private fun applyAllSettings() {
        val maxBands = equalizer?.numberOfBands?.toInt() ?: 0
        eqBands.forEach { (band, level) ->
            if (band < maxBands) {
                try { equalizer?.setBandLevel(band.toShort(), level.toShort()) } catch (e: Exception) {}
            }
        }
        try { bassBoost?.setStrength(bassBoostStrength.toShort()) } catch (e: Exception) {}
        try { virtualizer?.setStrength(virtualizerStrength.toShort()) } catch (e: Exception) {}
        try { presetReverb?.setPreset(reverbPreset.toShort()) } catch (e: Exception) {}
        
        equalizer?.enabled = isEqEnabled
        bassBoost?.enabled = isEqEnabled
        virtualizer?.enabled = isEqEnabled
        dynamicsProcessing?.enabled = isEqEnabled && limiterEnabled
        presetReverb?.enabled = isEqEnabled
    }

    private fun applyReplayGain() {
        if (!isReplayGainEnabled || currentTrackGain == 0f) {
            exoPlayer.volume = 1.0f
            return
        }
        val factor = Math.pow(10.0, (currentTrackGain / 20.0)).toFloat().coerceIn(0.1f, 2.0f)
        exoPlayer.volume = factor
        Log.d("MusicService", "Applied ReplayGain: $currentTrackGain dB -> Volume Factor: $factor")
    }

    private fun fadeVolume(targetVolume: Float, durationMs: Long) {
        volumeJob?.cancel()
        if (durationMs <= 0) {
            val baseVolume = if (isReplayGainEnabled && currentTrackGain != 0f) {
                Math.pow(10.0, (currentTrackGain / 20.0)).toFloat().coerceIn(0.1f, 2.0f)
            } else 1.0f
            exoPlayer.volume = targetVolume * baseVolume
            return
        }
        
        volumeJob = scope.launch {
            val baseVolume = if (isReplayGainEnabled && currentTrackGain != 0f) {
                Math.pow(10.0, (currentTrackGain / 20.0)).toFloat().coerceIn(0.1f, 2.0f)
            } else 1.0f
            
            val adjustedTarget = targetVolume * baseVolume
            val startVolume = exoPlayer.volume
            val steps = 12
            val delayStep = durationMs / steps
            val volumeStep = (adjustedTarget - startVolume) / steps

            for (i in 1..steps) {
                try {
                    exoPlayer.volume = startVolume + (volumeStep * i)
                } catch (e: Exception) { break }
                delay(delayStep)
            }
            try {
                exoPlayer.volume = adjustedTarget
            } catch (e: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MusicWidgetProvider.ACTION_PLAY_PAUSE -> if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            MusicWidgetProvider.ACTION_NEXT -> exoPlayer.seekToNext()
            MusicWidgetProvider.ACTION_PREV -> exoPlayer.seekToPrevious()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    private var crossfadeDurationMs: Long = 2000

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                volume = 1.0f
            }
        
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                updateWidget()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidget()
                if (isPlaying && equalizer == null) {
                    initAudioEffects(exoPlayer.audioSessionId)
                }
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                currentTrackGain = mediaItem?.mediaMetadata?.extras?.getFloat("track_gain") ?: 0f
                if (exoPlayer.isPlaying) {
                    fadeVolume(1.0f, 400)
                } else {
                    applyReplayGain()
                }
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                initAudioEffects(audioSessionId)
            }
        })

        scope.launch {
            while (true) {
                delay(200)
                try {
                    if (exoPlayer.isPlaying && exoPlayer.duration > 0 && crossfadeDurationMs > 0) {
                        val remainingMs = exoPlayer.duration - exoPlayer.currentPosition
                        if (remainingMs in 1..crossfadeDurationMs && exoPlayer.volume == 1.0f) {
                            fadeVolume(0.0f, crossfadeDurationMs - 200)
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.steel101.musicplayer.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val libraryCallback = object : MediaLibrarySession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand("SET_EQ_ENABLED", Bundle.EMPTY))
                    .add(SessionCommand("SET_EQ_BAND", Bundle.EMPTY))
                    .add(SessionCommand("SET_BASS_BOOST", Bundle.EMPTY))
                    .add(SessionCommand("SET_VIRTUALIZER", Bundle.EMPTY))
                    .add(SessionCommand("SET_CROSSFADE", Bundle.EMPTY))
                    .add(SessionCommand("SET_LIMITER_ENABLED", Bundle.EMPTY))
                    .add(SessionCommand("SET_SKIP_SILENCE_ENABLED", Bundle.EMPTY))
                    .add(SessionCommand("SET_GAPLESS_ENABLED", Bundle.EMPTY))
                    .add(SessionCommand("SET_REVERB_PRESET", Bundle.EMPTY))
                    .add(SessionCommand("SET_REPLAYGAIN_ENABLED", Bundle.EMPTY))
                    .add(SessionCommand("SET_PLAYBACK_SPEED", Bundle.EMPTY))
                    .add(SessionCommand("SET_PLAYBACK_PITCH", Bundle.EMPTY))
                    .add(SessionCommand("GET_AUDIO_SESSION_ID", Bundle.EMPTY))
                    .build()
                
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                controller: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<androidx.media3.common.MediaItem>> {
                val rootItem = androidx.media3.common.MediaItem.Builder()
                    .setMediaId("ROOT")
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setTitle("Music Library")
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                controller: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<androidx.media3.common.MediaItem>>> {
                val repository = (application as MusicApplication).repository
                
                return when (parentId) {
                    "ROOT" -> {
                        val rootChildren = ImmutableList.of(
                            androidx.media3.common.MediaItem.Builder()
                                .setMediaId("ALL_SONGS")
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle("All Songs")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .build()
                                ).build()
                        )
                        Futures.immediateFuture(LibraryResult.ofItemList(rootChildren, params))
                    }
                    "ALL_SONGS" -> {
                        val settableFuture = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<ImmutableList<androidx.media3.common.MediaItem>>>()
                        scope.launch {
                            val songs = repository.getSongs()
                            val items = songs.map { song ->
                                androidx.media3.common.MediaItem.Builder()
                                    .setMediaId(song.id.toString())
                                    .setUri(song.uri)
                                    .setMediaMetadata(
                                        androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle(song.title)
                                            .setArtist(song.artist)
                                            .setAlbumTitle(song.album)
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .build()
                                    )
                                    .build()
                            }
                            settableFuture.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        }
                        settableFuture
                    }
                    else -> Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    "SET_EQ_ENABLED" -> {
                        isEqEnabled = args.getBoolean("enabled")
                        applyAllSettings()
                    }
                    "SET_EQ_BAND" -> {
                        val band = args.getInt("band")
                        val level = args.getInt("level")
                        eqBands[band] = level
                        try {
                            equalizer?.setBandLevel(band.toShort(), level.toShort())
                        } catch (e: Exception) { Log.e("MusicService", "Error setting EQ band", e) }
                    }
                    "SET_BASS_BOOST" -> {
                        val strength = args.getInt("strength")
                        bassBoostStrength = strength
                        try {
                            bassBoost?.setStrength(strength.toShort())
                        } catch (e: Exception) { Log.e("MusicService", "Error setting Bass Boost", e) }
                    }
                    "SET_VIRTUALIZER" -> {
                        val strength = args.getInt("strength")
                        virtualizerStrength = strength
                        try {
                            virtualizer?.setStrength(strength.toShort())
                        } catch (e: Exception) { Log.e("MusicService", "Error setting Virtualizer", e) }
                    }
                    "SET_CROSSFADE" -> {
                        val duration = args.getInt("duration")
                        crossfadeDurationMs = duration.toLong() * 1000
                    }
                    "SET_LIMITER_ENABLED" -> {
                        limiterEnabled = args.getBoolean("enabled")
                        dynamicsProcessing?.enabled = isEqEnabled && limiterEnabled
                    }
                    "SET_SKIP_SILENCE_ENABLED" -> {
                        skipSilenceEnabled = args.getBoolean("enabled")
                        exoPlayer.skipSilenceEnabled = skipSilenceEnabled
                    }
                    "SET_GAPLESS_ENABLED" -> {
                        val enabled = args.getBoolean("enabled")
                        exoPlayer.pauseAtEndOfMediaItems = !enabled
                    }
                    "SET_REVERB_PRESET" -> {
                        val preset = args.getInt("preset")
                        reverbPreset = preset
                        try {
                            presetReverb?.setPreset(preset.toShort())
                        } catch (e: Exception) { Log.e("MusicService", "Error setting Reverb", e) }
                    }
                    "SET_REPLAYGAIN_ENABLED" -> {
                        isReplayGainEnabled = args.getBoolean("enabled")
                        applyReplayGain()
                    }
                    "SET_PLAYBACK_SPEED" -> {
                        val speed = args.getFloat("speed")
                        exoPlayer.setPlaybackSpeed(speed)
                    }
                    "SET_PLAYBACK_PITCH" -> {
                        val pitch = args.getFloat("pitch")
                        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(exoPlayer.playbackParameters.speed, pitch)
                    }
                    "GET_AUDIO_SESSION_ID" -> {
                        val resultBundle = Bundle().apply {
                            putInt("audio_session_id", exoPlayer.audioSessionId)
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                    }
                    "FADE_OUT" -> {
                        val duration = args.getInt("duration")
                        fadeVolume(0.0f, duration.toLong())
                    }
                    "FADE_IN" -> {
                        val duration = args.getInt("duration")
                        fadeVolume(1.0f, duration.toLong())
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaLibrarySession.Builder(this, exoPlayer, libraryCallback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
        
        setupVolumeMemory()
    }

    private var currentDeviceId: String? = null
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            updateVolumeForCurrentDevice()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            updateVolumeForCurrentDevice()
        }
    }

    private fun setupVolumeMemory() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        updateVolumeForCurrentDevice()
    }

    private fun updateVolumeForCurrentDevice() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val device = devices.firstOrNull { it.isSink }
        val deviceId = device?.let { "${it.type}_${it.address}" } ?: "default"

        if (deviceId != currentDeviceId) {
            // Save old volume
            currentDeviceId?.let { oldId ->
                val sharedPrefs = getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putFloat("volume_$oldId", exoPlayer.volume).apply()
            }
            
            // Restore new volume
            currentDeviceId = deviceId
            val sharedPrefs = getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
            val savedVolume = sharedPrefs.getFloat("volume_$deviceId", 1.0f)
            fadeVolume(savedVolume, 500)
        }
    }

    private fun updateWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, MusicWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val currentMediaItem = exoPlayer.currentMediaItem
        
        scope.launch {
            val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri
            val bitmap = if (artworkUri != null) {
                try {
                    val loader = ImageLoader(this@MusicService)
                    val request = ImageRequest.Builder(this@MusicService)
                        .data(artworkUri)
                        .size(128, 128)
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        (result.drawable as? BitmapDrawable)?.bitmap
                    } else null
                } catch (e: Exception) { null }
            } else null

            withContext(Dispatchers.Main) {
                for (appWidgetId in appWidgetIds) {
                    MusicWidgetProvider.updateAppWidget(
                        context = this@MusicService,
                        appWidgetManager = appWidgetManager,
                        appWidgetId = appWidgetId,
                        title = currentMediaItem?.mediaMetadata?.title?.toString(),
                        artist = currentMediaItem?.mediaMetadata?.artist?.toString(),
                        isPlaying = exoPlayer.isPlaying,
                        albumArt = bitmap
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession?.run {
            player.stop()
            player.release()
            release()
            mediaSession = null
        }
        stopSelf()
    }
}
