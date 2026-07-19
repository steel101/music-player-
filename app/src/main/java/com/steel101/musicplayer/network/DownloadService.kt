package com.steel101.musicplayer.network

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object DownloadStatus {
    private val _downloadingTracks = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingTracks: StateFlow<Map<String, Float>> = _downloadingTracks.asStateFlow()
    
    private val _onDownloadFinished = MutableStateFlow<String?>(null)
    val onDownloadFinished: StateFlow<String?> = _onDownloadFinished.asStateFlow()

    fun updateProgress(trackKey: String, progress: Float) {
        _downloadingTracks.value = _downloadingTracks.value + (trackKey to progress)
    }
    
    fun removeTrack(trackKey: String) {
        _downloadingTracks.value = _downloadingTracks.value - trackKey
    }

    fun notifyFinished(trackKey: String) {
        _onDownloadFinished.value = trackKey
        _onDownloadFinished.value = null
    }
}

class DownloadService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val CHANNEL_ID = "downloads"
    private val NOTIFICATION_ID_BASE = 1000
    private val activeJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val activeCalls = java.util.concurrent.ConcurrentHashMap<String, okhttp3.Call>()
    private val ACTION_CANCEL = "com.steel101.musicplayer.CANCEL_DOWNLOAD"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val trackKey = intent.getStringExtra("trackKey")
            trackKey?.let { cancelDownload(it) }
            return START_NOT_STICKY
        }

        val trackTitle = intent?.getStringExtra("trackTitle") ?: return START_NOT_STICKY
        val artistName = intent.getStringExtra("artistName") ?: return START_NOT_STICKY
        val trackKey = "$artistName - $trackTitle"

        if (DownloadStatus.downloadingTracks.value.containsKey(trackKey)) return START_NOT_STICKY

        val initialNotification = createInitialNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_BASE, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID_BASE, initialNotification)
        }

        val job = serviceScope.launch {
            startDownload(trackTitle, artistName, trackKey)
        }
        activeJobs[trackKey] = job

        return START_NOT_STICKY
    }

    private fun cancelDownload(trackKey: String) {
        activeCalls[trackKey]?.cancel()
        activeCalls.remove(trackKey)
        activeJobs[trackKey]?.cancel()
        activeJobs.remove(trackKey)
        DownloadStatus.removeTrack(trackKey)
        if (activeJobs.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Preparing download...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private suspend fun startDownload(trackTitle: String, artistName: String, trackKey: String) {
        DownloadStatus.updateProgress(trackKey, 0f)
        showNotification(trackTitle, trackKey, true, 0)

        val safeTrackKey = trackKey.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        var outputFile: File? = null

        try {
            val youtube = ServiceList.YouTube
            var searchExtractor = youtube.getSearchExtractor(trackKey)
            searchExtractor.fetchPage()
            
            var videoItem = searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().firstOrNull()
            if (videoItem == null) {
                searchExtractor = youtube.getSearchExtractor(trackTitle)
                searchExtractor.fetchPage()
                videoItem = searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().firstOrNull()
            }

            if (videoItem != null && videoItem.url != null) {
                
                currentCoroutineContext().ensureActive()
                val streamExtractor = youtube.getStreamExtractor(videoItem.url)
                streamExtractor.fetchPage()

                val thumbnails = streamExtractor.thumbnails
                val thumbnailUrl = if (thumbnails != null && thumbnails.isNotEmpty()) {
                    thumbnails.last().url
                } else {
                    videoItem.thumbnails?.lastOrNull()?.url
                }
                
                val audioStream = streamExtractor.audioStreams
                    .filter { it.format?.suffix?.contains("m4a") == true }
                    .maxByOrNull { it.bitrate }
                    ?: streamExtractor.audioStreams.maxByOrNull { it.bitrate }

                val streamUrl = audioStream?.url
                if (streamUrl != null) {
                    val musicFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    if (!musicFolder.exists()) musicFolder.mkdirs()
                    
                    val extension = audioStream.format?.suffix ?: "m4a"
                    val finalFile = File(musicFolder, "$safeTrackKey.$extension")
                    outputFile = finalFile
                    
                    val request = Request.Builder()
                        .url(streamUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()

                    val call = client.newCall(request)
                    activeCalls[trackKey] = call
                    
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Failed: ${response.code}")
                        
                        val body = response.body ?: throw Exception("Empty body")
                        val contentLength = body.contentLength()
                        
                        body.byteStream().use { input ->
                            FileOutputStream(finalFile).use { output ->
                                val data = ByteArray(8192)
                                var totalRead = 0L
                                var bytesRead: Int
                                var lastReported = -1
                                
                                while (input.read(data).also { bytesRead = it } != -1) {
                                    currentCoroutineContext().ensureActive()
                                    output.write(data, 0, bytesRead)
                                    totalRead += bytesRead
                                    if (contentLength > 0) {
                                        val progress = totalRead.toFloat() / contentLength
                                        DownloadStatus.updateProgress(trackKey, progress)
                                        
                                        val current = (progress * 100).toInt()
                                        if (current > lastReported) {
                                            lastReported = current
                                            showNotification(trackTitle, trackKey, true, current)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    activeCalls.remove(trackKey)

                    thumbnailUrl?.let { url ->
                        downloadThumbnail(url, trackKey)
                    }

                    MediaScannerConnection.scanFile(this@DownloadService, arrayOf(finalFile.absolutePath), null) { _, _ ->
                        DownloadStatus.notifyFinished(trackKey)
                    }
                    
                    showNotification(trackTitle, trackKey, false, 100, true)
                } else throw Exception("No audio stream")
            } else throw Exception("No video found")
        } catch (e: Exception) {
            val isCancelled = e is CancellationException || 
                             (e is java.io.IOException && e.message?.contains("Canceled", ignoreCase = true) == true)
            
            if (isCancelled) {
                outputFile?.delete()
                android.util.Log.d("DownloadService", "Download cancelled and file deleted: $trackKey")
            } else {
                android.util.Log.e("DownloadService", "Download failed: $trackKey", e)
                showNotification(trackTitle, trackKey, false, 0, false)
            }
        } finally {
            activeCalls.remove(trackKey)
            activeJobs.remove(trackKey)
            DownloadStatus.removeTrack(trackKey)
            if (activeJobs.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private fun downloadThumbnail(url: String, trackKey: String) {
        try {
            val safeTrackKey = trackKey.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            android.util.Log.d("DownloadService", "Downloading thumbnail: $url for $safeTrackKey")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        val cacheDir = File(cacheDir, "artwork")
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        val file = File(cacheDir, "$safeTrackKey.jpg")
                        
                        body.byteStream().use { input ->
                            FileOutputStream(file).use { output ->
                                input.copyTo(output)
                            }
                        }
                        android.util.Log.d("DownloadService", "Thumbnail saved to: ${file.absolutePath} (Size: ${file.length()})")
                    }
                } else {
                    android.util.Log.e("DownloadService", "Thumbnail download failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadService", "Error saving thumbnail", e)
        }
    }

    private fun showNotification(title: String, trackKey: String, isOngoing: Boolean, progress: Int, success: Boolean = true) {
        val ongoingDownloads = DownloadStatus.downloadingTracks.value
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (isOngoing) {
            val cancelIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra("trackKey", trackKey)
            }
            val cancelPendingIntent = PendingIntent.getService(
                this, 
                trackKey.hashCode(), 
                cancelIntent, 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)

            if (ongoingDownloads.size > 1) {
                builder.setContentTitle("Downloading ${ongoingDownloads.size} tracks")
                val totalProgress = ongoingDownloads.values.sum() / ongoingDownloads.size
                builder.setProgress(100, (totalProgress * 100).toInt(), false)
                builder.setContentText("Total progress: ${(totalProgress * 100).toInt()}%")
            } else if (ongoingDownloads.isNotEmpty()) {
                val currentTitle = ongoingDownloads.keys.first().substringAfter(" - ")
                val currentProgress = (ongoingDownloads.values.first() * 100).toInt()
                builder.setContentTitle("Downloading $currentTitle")
                builder.setContentText("$currentProgress%")
                builder.setProgress(100, currentProgress, false)
            } else {
                builder.setContentTitle("Downloading...")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID_BASE, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID_BASE, builder.build())
            }
        } else {
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (success) "Download Complete" else "Download Failed")
                .setContentText(if (success) "Finished downloading $title" else "Failed to download $title")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)

            manager.notify(trackKey.hashCode(), builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
