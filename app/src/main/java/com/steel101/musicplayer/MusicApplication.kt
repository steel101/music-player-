package com.steel101.musicplayer

import android.app.Application
import com.steel101.musicplayer.data.AppDatabase
import com.steel101.musicplayer.data.MusicRepository
import com.steel101.musicplayer.network.AppDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

class MusicApplication : Application() {
    lateinit var repository: MusicRepository

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = MusicRepository(this, database.metadataDao())
        NewPipe.init(AppDownloader(), Localization("US", "en"))
    }
}
