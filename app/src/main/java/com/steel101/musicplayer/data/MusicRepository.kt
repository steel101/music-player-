package com.steel101.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.content.ContentValues
import androidx.core.net.toUri
import android.util.Log
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import com.kyant.taglib.Picture

class MusicRepository(private val context: Context, val metadataDao: MetadataDao) {

    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        metadataDao.deletePlaylist(playlist)
    }

    suspend fun updateMediaStore(song: Song) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, song.title)
                put(MediaStore.Audio.Media.ARTIST, song.artist)
                put(MediaStore.Audio.Media.ALBUM, song.album)
                if (song.year > 0) put(MediaStore.Audio.Media.YEAR, song.year)
                song.genre?.let { put(MediaStore.Audio.Media.GENRE, it) }
                if (song.path.isNotEmpty()) {
                    put(MediaStore.Audio.Media.DATA, song.path)
                }
            }
            context.contentResolver.update(song.uri, values, null, null)
            
            if (song.path.isNotEmpty()) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(song.path),
                    null
                ) { path, uri ->
                    Log.d("MusicRepository", "MediaScanner scan completed for path: $path, uri: $uri")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun scanMusicFolder() = withContext(Dispatchers.IO) {
        val musicFolder = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        if (musicFolder.exists()) {
            val files = musicFolder.listFiles()?.map { it.absolutePath }?.toTypedArray() ?: emptyArray()
            if (files.isNotEmpty()) {
                android.media.MediaScannerConnection.scanFile(context, files, null) { path, uri ->
                    Log.d("MusicRepository", "Broad scan completed for: $path")
                }
            }
        }
    }

    suspend fun writeTagsToFile(song: Song) = withContext(Dispatchers.IO) {
        val originalFile = File(song.path)
        
        val extension = originalFile.extension.ifEmpty { "mp3" }
        val tempFile = File(context.cacheDir, "temp_edit_${System.currentTimeMillis()}.$extension")
        
        try {
            Log.d("MusicRepository", "Writing tags to file via temp: ${song.path}")
            
            val bytesCopied = context.contentResolver.openInputStream(song.uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not open input stream for ${song.uri}")

            Log.d("MusicRepository", "Copied $bytesCopied bytes to temp file (size: ${tempFile.length()})")

            if (!tempFile.exists() || (tempFile.length() == 0L)) {
                throw Exception("Failed to create temp file for editing")
            }
            
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
                performWrite(pfd, song)
            }

            context.contentResolver.openOutputStream(song.uri, "rwt")?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not open output stream for ${song.uri}")

            Log.d("MusicRepository", "Audio file committed successfully via MediaStore")
            updateMediaStore(song)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error writing tags to file: ${e.message}", e)
            throw e 
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun performWrite(pfd: ParcelFileDescriptor, song: Song) {
        Log.d("MusicRepository", "performWrite via TagLib")
        
        try {
            val metadata = TagLib.getMetadata(pfd.dup().detachFd())
            val propertyMap = metadata?.propertyMap?.let { HashMap(it) } ?: HashMap<String, Array<String>>()
            
            propertyMap["TITLE"] = arrayOf(song.title)
            propertyMap["ARTIST"] = arrayOf(song.artist)
            propertyMap["ALBUM"] = arrayOf(song.album)
            if (song.year > 0) propertyMap["DATE"] = arrayOf(song.year.toString())
            song.genre?.let { propertyMap["GENRE"] = arrayOf(it) }
            song.lyrics?.let { propertyMap["LYRICS"] = arrayOf(it) }
            song.artistMbid?.let { propertyMap["MUSICBRAINZ_ARTISTID"] = arrayOf(it) }
            song.albumMbid?.let { propertyMap["MUSICBRAINZ_ALBUMID"] = arrayOf(it) }
            
            TagLib.savePropertyMap(pfd.dup().detachFd(), propertyMap)
            Log.d("MusicRepository", "TagLib saved property map successfully")
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to set text metadata via TagLib", e)
        }

        val imageUrl = song.albumImageUrl
        if (imageUrl != null) {
            var bytes: ByteArray? = null
            try {
                if (imageUrl.startsWith("http")) {
                    bytes = downloadUrlBytes(imageUrl)
                } else {
                    try {
                        val cleanPath = imageUrl.removePrefix("file://").removePrefix("file:")
                        val file = File(cleanPath)
                        
                        if (file.exists()) {
                            bytes = file.readBytes()
                        } else {
                            Log.w("MusicRepository", "Image file not found at path: ${file.absolutePath}, trying ContentResolver...")
                            context.contentResolver.openInputStream(imageUrl.toUri())?.use { bytes = it.readBytes() }
                        }
                    } catch (e: Exception) {
                        Log.w("MusicRepository", "Direct File stream failed for $imageUrl: ${e.message}")
                        context.contentResolver.openInputStream(imageUrl.toUri())?.use { bytes = it.readBytes() }
                    }
                }

                if (bytes == null && imageUrl.startsWith("http")) {
                    Log.i("MusicRepository", "Primary artwork download failed for $imageUrl, trying iTunes search fallback...")
                    val fallbackUrl = fetchItunesArtworkUrl(song.artist, song.album.ifEmpty { song.title })
                    if (fallbackUrl != null) {
                        Log.i("MusicRepository", "Found iTunes fallback artwork URL: $fallbackUrl")
                        bytes = downloadUrlBytes(fallbackUrl)
                    }
                }

                if (bytes != null) {
                    val optimizedBytes = compressAndResizeArtwork(bytes, 300)
                    val mimeType = detectMimeType(optimizedBytes)
                    
                    val newCover = Picture(
                        data = optimizedBytes,
                        description = "Front Cover",
                        pictureType = "Front Cover",
                        mimeType = mimeType
                    )
                    
                    val saved = TagLib.savePictures(pfd.dup().detachFd(), arrayOf(newCover))
                    Log.d("MusicRepository", "TagLib saved artwork successfully: $saved")
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Failed to set artwork via TagLib", e)
            }
        }
    }

    private fun downloadUrlBytes(urlStr: String): ByteArray? {
        var currentUrl = urlStr
        var redirectCount = 0
        val maxRedirects = 5

        while (redirectCount < maxRedirects) {
            val url = try {
                URL(currentUrl)
            } catch (e: Exception) {
                Log.e("MusicRepository", "Malformed URL: $currentUrl", e)
                return null
            }
            val connection = url.openConnection() as? java.net.HttpURLConnection ?: return null
            try {
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "MusicPlayer/1.0.0 (https://github.com/example/musicplayer)")
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode
                Log.d("MusicRepository", "Downloading $currentUrl - Response Code: $responseCode")

                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    return connection.inputStream.use { it.readBytes() }
                } else if (responseCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP || 
                           responseCode == java.net.HttpURLConnection.HTTP_MOVED_PERM || 
                           responseCode == 307 || responseCode == 308) {
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        currentUrl = location
                        redirectCount++
                        continue
                    } else {
                        Log.w("MusicRepository", "Redirect response received but Location header is missing.")
                        return null
                    }
                } else if (responseCode == java.net.HttpURLConnection.HTTP_NOT_FOUND) {
                    Log.i("MusicRepository", "No artwork found (404) at $currentUrl")
                    return null
                } else {
                    Log.w("MusicRepository", "HTTP request failed with code: $responseCode for URL: $currentUrl")
                    return null
                }
            } catch (e: java.io.FileNotFoundException) {
                Log.i("MusicRepository", "No artwork found (FileNotFoundException) at $currentUrl")
                return null
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error during download of $currentUrl: ${e.message}")
                return null
            } finally {
                connection.disconnect()
            }
        }
        Log.w("MusicRepository", "Too many redirects for URL: $urlStr")
        return null
    }

    private fun fetchItunesArtworkUrl(artist: String, albumOrTitle: String): String? {
        try {
            val query = java.net.URLEncoder.encode("$artist $albumOrTitle", "UTF-8")
            val urlStr = "https://itunes.apple.com/search?term=$query&entity=album&limit=1"
            val responseBytes = downloadUrlBytes(urlStr) ?: return null
            val response = String(responseBytes, Charsets.UTF_8)
            val artworkKey = "\"artworkUrl100\":\""
            val index = response.indexOf(artworkKey)
            if (index != -1) {
                val start = index + artworkKey.length
                val end = response.indexOf("\"", start)
                if (end != -1) {
                    val url = response.substring(start, end).replace("\\/", "/")
                    return url.replace("100x100bb", "1000x1000bb")
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to fetch iTunes fallback artwork url", e)
        }
        return null
    }

    private fun detectMimeType(bytes: ByteArray): String {
        if (bytes.size >= 4) {
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
                return "image/png"
            }
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
                return "image/jpeg"
            }
            if (bytes[0] == 'G'.toByte() && bytes[1] == 'I'.toByte() && bytes[2] == 'F'.toByte() && bytes[3] == '8'.toByte()) {
                return "image/gif"
            }
        }
        return "image/jpeg"
    }

    private fun compressAndResizeArtwork(bytes: ByteArray, maxDimension: Int = 300): ByteArray {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            
            val width = options.outWidth
            val height = options.outHeight
            
            if (width <= 0 || height <= 0) return bytes
            
            if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                val newWidth = if (ratio > 1) maxDimension else (maxDimension * ratio).toInt()
                val newHeight = if (ratio > 1) (maxDimension / ratio).toInt() else maxDimension
                
                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(width, height, maxDimension, maxDimension)
                }
                val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return bytes
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                
                val outputStream = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                originalBitmap.recycle()
                scaledBitmap.recycle()
                outputStream.toByteArray()
            } else {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                bitmap.recycle()
                outputStream.toByteArray()
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to compress/resize artwork, using original bytes", e)
            bytes
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    suspend fun renameFileToMatchMetadata(song: Song): String? = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(song.path)
            val extension = oldFile.extension
            val newFileName = "${song.artist} - ${song.title}.$extension"
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")

            if (oldFile.name == newFileName || song.filename == newFileName) {
                return@withContext null
            }
            
            val hasDirectAccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                true
            }

            if (hasDirectAccess) {
                if (!oldFile.exists()) return@withContext null
                val newFile = File(oldFile.parent, newFileName)
                if (oldFile.renameTo(newFile)) {
                    val newPath = newFile.absolutePath
                    val existing = metadataDao.getMetadata(song.path)
                    if (existing != null) {
                        metadataDao.deleteMetadata(song.path)
                        metadataDao.insertMetadata(existing.copy(songKey = newPath))
                    }
                    
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DATA, newPath)
                        put(MediaStore.Audio.Media.DISPLAY_NAME, newFileName)
                    }
                    context.contentResolver.update(song.uri, values, null, null)

                    android.media.MediaScannerConnection.scanFile(context, arrayOf(newPath), null, null)

                    return@withContext newPath
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, newFileName)
                }
                val rows = context.contentResolver.update(song.uri, values, null, null)
                if (rows > 0) {
                    return@withContext newFileName
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error renaming file", e)
            throw e
        }
        null
    }

    suspend fun getSongs(): List<Song> = withContext(Dispatchers.IO) {
        val excludedFolders = metadataDao.getAllExcludedFolders().map { it.path }
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.GENRE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val retriever = MediaMetadataRetriever()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val displayColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val genreColumn = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                var title = cursor.getString(titleColumn) ?: "Unknown"
                var artist = cursor.getString(artistColumn) ?: "Unknown"
                val album = cursor.getString(albumColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val track = cursor.getInt(trackColumn)
                val year = cursor.getInt(yearColumn)
                val filename = cursor.getString(displayColumn) ?: ""
                val path = cursor.getString(dataColumn) ?: ""
                val dateAdded = cursor.getLong(dateAddedColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                val genre = if (genreColumn != -1) cursor.getString(genreColumn) else null

                if (!File(path).exists()) continue
                
                if (excludedFolders.any { path.startsWith(it) }) continue

                val songKey = path
                val cached = metadataDao.getMetadata(songKey)

                if (artist == "Unknown" || artist == "<unknown>") {
                    val (cleanedArtist, cleanedTitle) = MetadataCleaner.cleanFilename(filename)
                    artist = cleanedArtist
                    title = cleanedTitle
                } else {
                    artist = MetadataCleaner.cleanString(artist)
                    title = MetadataCleaner.cleanString(title)
                }

                var hasEmbeddedArt = false
                var needsCheck = true
                
                if (cached?.hasEmbeddedArtChecked == true) {
                    hasEmbeddedArt = cached.hasEmbeddedArt
                    needsCheck = false
                }

                if (needsCheck) {
                    try {
                        retriever.setDataSource(context, uri)
                        hasEmbeddedArt = retriever.embeddedPicture != null
                        
                        metadataDao.insertMetadata(MetadataEntity(
                            songKey = songKey,
                            artistMbid = cached?.artistMbid,
                            albumMbid = cached?.albumMbid,
                            artistImageUrl = cached?.artistImageUrl,
                            albumImageUrl = cached?.albumImageUrl,
                            enrichedTitle = cached?.enrichedTitle ?: title,
                            enrichedArtist = cached?.enrichedArtist ?: artist,
                            enrichedAlbum = cached?.enrichedAlbum ?: album,
                            genre = cached?.genre ?: genre,
                            manualOverride = cached?.manualOverride ?: false,
                            isHidden = cached?.isHidden ?: false,
                            hasEmbeddedArt = hasEmbeddedArt,
                            hasEmbeddedArtChecked = true,
                            isFavorite = cached?.isFavorite ?: false,
                            playCount = cached?.playCount ?: 0,
                            lastPlayed = cached?.lastPlayed ?: 0,
                            totalPlayTimeMs = cached?.totalPlayTimeMs ?: 0,
                            manualNotPodcast = cached?.manualNotPodcast ?: false,
                            lyrics = cached?.lyrics,
                            trackGain = cached?.trackGain
                        ))
                    } catch (e: Exception) {}
                }

                var trackGain = cached?.trackGain
                var lyrics = cached?.lyrics
                if (trackGain == null || lyrics == null) {
                    try {
                        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            val metadata = TagLib.getMetadata(pfd.dup().detachFd())
                            if (trackGain == null) {
                                val gainStr = metadata?.propertyMap?.get("REPLAYGAIN_TRACK_GAIN")?.firstOrNull()
                                             ?: metadata?.propertyMap?.get("replaygain_track_gain")?.firstOrNull()
                                trackGain = gainStr?.replace(" dB", "")?.toFloatOrNull()
                            }
                            if (lyrics == null) {
                                lyrics = metadata?.propertyMap?.get("LYRICS")?.firstOrNull()
                                         ?: metadata?.propertyMap?.get("UNSYNCEDLYRICS")?.firstOrNull()
                                         ?: metadata?.propertyMap?.get("USLT")?.firstOrNull()
                            }
                        }
                    } catch (e: Exception) {}
                }

                songs.add(Song(
                    id = id,
                    title = cached?.enrichedTitle ?: title,
                    artist = cached?.enrichedArtist ?: artist,
                    album = cached?.enrichedAlbum ?: album,
                    duration = duration,
                    uri = uri,
                    albumId = albumId,
                    trackNumber = track,
                    year = year,
                    path = path,
                    filename = filename,
                    artistMbid = cached?.artistMbid,
                    albumMbid = cached?.albumMbid,
                    artistImageUrl = cached?.artistImageUrl,
                    albumImageUrl = cached?.albumImageUrl,
                    genre = cached?.genre ?: genre,
                    hasEmbeddedArt = hasEmbeddedArt,
                    isHidden = cached?.isHidden ?: false,
                    manualOverride = cached?.manualOverride ?: false,
                    isFavorite = cached?.isFavorite ?: false,
                    playCount = cached?.playCount ?: 0,
                    lastPlayed = cached?.lastPlayed ?: 0,
                    totalPlayTimeMs = cached?.totalPlayTimeMs ?: 0,
                    dateAdded = dateAdded,
                    manualNotPodcast = cached?.manualNotPodcast ?: false,
                    lyrics = lyrics,
                    trackGain = trackGain
                ))
            }
        }
        try { retriever.release() } catch (e: Exception) {}
        songs.distinctBy { it.path }
    }

    suspend fun saveMetadata(song: Song, isManual: Boolean = false) {
        val songKey = song.path
        val existing = metadataDao.getMetadata(songKey)
        
        if (isManual || existing?.manualOverride != true) {
            metadataDao.insertMetadata(MetadataEntity(
                songKey = songKey,
                artistMbid = song.artistMbid,
                albumMbid = song.albumMbid,
                artistImageUrl = song.artistImageUrl,
                albumImageUrl = song.albumImageUrl,
                enrichedTitle = song.title,
                enrichedArtist = song.artist,
                enrichedAlbum = song.album,
                genre = song.genre,
                manualOverride = isManual || existing?.manualOverride == true,
                isHidden = song.isHidden,
                hasEmbeddedArt = song.hasEmbeddedArt,
                hasEmbeddedArtChecked = true,
                isFavorite = song.isFavorite,
                playCount = song.playCount,
                lastPlayed = song.lastPlayed,
                totalPlayTimeMs = song.totalPlayTimeMs,
                manualNotPodcast = song.manualNotPodcast,
                lyrics = song.lyrics,
                trackGain = song.trackGain
            ))
        }
    }

    suspend fun setManualNotPodcast(song: Song, manualNotPodcast: Boolean) {
        metadataDao.setManualNotPodcast(song.path, manualNotPodcast)
    }

    suspend fun toggleFavorite(song: Song) {
        metadataDao.setFavorite(song.path, !song.isFavorite)
    }

    suspend fun recordPlay(song: Song) {
        metadataDao.incrementPlayCount(song.path)
    }

    suspend fun addPlayTime(song: Song, durationMs: Long) {
        metadataDao.addPlayTime(song.path, durationMs)
    }

    suspend fun createPlaylist(name: String): Long {
        return metadataDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun getPlaylists(): List<PlaylistEntity> {
        return metadataDao.getAllPlaylists()
    }

    suspend fun addSongToPlaylist(playlistId: Long, songPath: String) {
        metadataDao.addSongToPlaylist(PlaylistSongEntity(playlistId, songPath))
    }

    suspend fun getSongsInPlaylist(playlistId: Long): List<String> {
        return metadataDao.getSongsInPlaylist(playlistId)
    }

    suspend fun addSearchQuery(query: String) {
        metadataDao.insertSearchQuery(SearchHistoryEntity(query))
    }

    suspend fun getRecentSearches(): List<String> {
        return metadataDao.getRecentSearches().map { it.query }
    }
    suspend fun toggleHideSong(song: Song) {
        val songKey = song.path
        val existing = metadataDao.getMetadata(songKey)
        if (existing != null) {
            metadataDao.insertMetadata(existing.copy(isHidden = !existing.isHidden))
        } else {
            metadataDao.insertMetadata(MetadataEntity(
                songKey = songKey,
                artistMbid = song.artistMbid,
                albumMbid = song.albumMbid,
                artistImageUrl = song.artistImageUrl,
                albumImageUrl = song.albumImageUrl,
                enrichedTitle = song.title,
                enrichedArtist = song.artist,
                enrichedAlbum = song.album,
                genre = song.genre,
                isHidden = true,
                hasEmbeddedArt = song.hasEmbeddedArt,
                hasEmbeddedArtChecked = true
            ))
        }
    }

    suspend fun clearCache() {
        metadataDao.clearAllMetadata()
    }

    suspend fun updateArtistImage(artistName: String, imageUrl: String) {
        metadataDao.updateArtistImage(artistName, imageUrl)
    }

    suspend fun getExcludedFolders(): List<String> {
        return metadataDao.getAllExcludedFolders().map { it.path }
    }

    suspend fun addExcludedFolder(path: String) {
        metadataDao.insertExcludedFolder(ExcludedFolderEntity(path))
    }

    suspend fun removeExcludedFolder(path: String) {
        metadataDao.deleteExcludedFolder(ExcludedFolderEntity(path))
    }
}
