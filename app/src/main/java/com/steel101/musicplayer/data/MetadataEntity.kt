package com.steel101.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_metadata")
data class MetadataEntity(
    @PrimaryKey val songKey: String,
    val artistMbid: String?,
    val albumMbid: String?,
    val artistImageUrl: String?,
    val albumImageUrl: String?,
    val enrichedTitle: String?,
    val enrichedArtist: String?,
    val enrichedAlbum: String?,
    val genre: String? = null,
    val manualOverride: Boolean = false,
    val isHidden: Boolean = false,
    val hasEmbeddedArt: Boolean = false,
    val hasEmbeddedArtChecked: Boolean = false,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTimeMs: Long = 0,
    val manualNotPodcast: Boolean = false,
    val lyrics: String? = null,
    val trackGain: Float? = null,
    val bpm: Int? = null,
    val musicalKey: String? = null,
    val isVideo: Boolean = false,
    val composer: String? = null,
    val discNumber: Int = 0,
    val albumArtist: String? = null,
    val comment: String? = null
)

@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rulesJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songPath"]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songPath: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val path: String
)
