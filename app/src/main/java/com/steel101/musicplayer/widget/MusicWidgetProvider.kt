package com.steel101.musicplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.graphics.Bitmap
import com.steel101.musicplayer.MainActivity
import com.steel101.musicplayer.R
import com.steel101.musicplayer.player.MusicService

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.steel101.musicplayer.PLAY_PAUSE"
        const val ACTION_NEXT = "com.steel101.musicplayer.NEXT"
        const val ACTION_PREV = "com.steel101.musicplayer.PREV"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, 
                           title: String? = null, artist: String? = null, isPlaying: Boolean = false,
                           albumArt: Bitmap? = null) {
            
            val views = RemoteViews(context.packageName, R.layout.music_widget)
            
            views.setTextViewText(R.id.widget_title, title ?: "Not Playing")
            views.setTextViewText(R.id.widget_artist, artist ?: "Select a song")
            
            if (albumArt != null) {
                views.setImageViewBitmap(R.id.widget_album_art, albumArt)
            } else {
                views.setImageViewResource(R.id.widget_album_art, android.R.drawable.ic_menu_report_image)
            }

            views.setImageViewResource(R.id.widget_play_pause, 
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_album_art, pendingIntent)

            val playIntent = Intent(context, MusicWidgetProvider::class.java).apply { action = ACTION_PLAY_PAUSE }
            views.setOnClickPendingIntent(R.id.widget_play_pause, 
                PendingIntent.getBroadcast(context, 1, playIntent, PendingIntent.FLAG_IMMUTABLE))

            val nextIntent = Intent(context, MusicWidgetProvider::class.java).apply { action = ACTION_NEXT }
            views.setOnClickPendingIntent(R.id.widget_next, 
                PendingIntent.getBroadcast(context, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE))

            val prevIntent = Intent(context, MusicWidgetProvider::class.java).apply { action = ACTION_PREV }
            views.setOnClickPendingIntent(R.id.widget_prev, 
                PendingIntent.getBroadcast(context, 3, prevIntent, PendingIntent.FLAG_IMMUTABLE))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_PLAY_PAUSE || action == ACTION_NEXT || action == ACTION_PREV) {
            val serviceIntent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            context.startService(serviceIntent)
        }
    }
}