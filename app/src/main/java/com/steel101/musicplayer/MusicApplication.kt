package com.steel101.musicplayer

import android.app.Application
import com.steel101.musicplayer.network.AppDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

class MusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(AppDownloader(), Localization("US", "en"))
    }
}
