package com.steel101.musicplayer.data

import androidx.room.*

@Dao
interface MetadataDao {
    @Query("SELECT * FROM music_metadata WHERE songKey = :key")
    suspend fun getMetadata(key: String): MetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: MetadataEntity)

    @Query("SELECT * FROM music_metadata")
    suspend fun getAllMetadata(): List<MetadataEntity>

    @Query("DELETE FROM music_metadata")
    suspend fun clearAllMetadata()

    @Query("UPDATE music_metadata SET artistImageUrl = :url, manualOverride = 1 WHERE enrichedArtist = :artistName")
    suspend fun updateArtistImage(artistName: String, url: String)

    @Query("UPDATE music_metadata SET isFavorite = :isFavorite WHERE songKey = :songPath")
    suspend fun setFavorite(songPath: String, isFavorite: Boolean)

    @Query("UPDATE music_metadata SET manualNotPodcast = :manualNotPodcast WHERE songKey = :songPath")
    suspend fun setManualNotPodcast(songPath: String, manualNotPodcast: Boolean)

    @Query("UPDATE music_metadata SET playCount = playCount + 1, lastPlayed = :timestamp WHERE songKey = :songPath")
    suspend fun incrementPlayCount(songPath: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE music_metadata SET totalPlayTimeMs = totalPlayTimeMs + :durationMs WHERE songKey = :songPath")
    suspend fun addPlayTime(songPath: String, durationMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(playlistSong: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songPath = :songPath")
    suspend fun removeSongFromPlaylist(playlistId: Long, songPath: String)

    @Query("SELECT songPath FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getSongsInPlaylist(playlistId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(search: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentSearches(): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM music_metadata WHERE songKey = :songPath")
    suspend fun deleteMetadata(songPath: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExcludedFolder(folder: ExcludedFolderEntity)

    @Query("SELECT * FROM excluded_folders")
    suspend fun getAllExcludedFolders(): List<ExcludedFolderEntity>

    @Delete
    suspend fun deleteExcludedFolder(folder: ExcludedFolderEntity)
}
