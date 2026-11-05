package com.cvc953.localplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.cvc953.localplayer.MainActivity
import com.cvc953.localplayer.R
import com.cvc953.localplayer.Services.MusicService
import com.cvc953.localplayer.preferences.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayerWidget {
    companion object {
        suspend fun refresh(context: Context) {
            withContext(Dispatchers.IO) {
                updateAll(context.applicationContext)
                updateAllSquare(context.applicationContext)
            }
        }

        suspend fun refreshSquare(context: Context) {
            withContext(Dispatchers.IO) {
                updateAllSquare(context.applicationContext)
            }
        }

        fun updateAll(context: Context) {
            updateWidget(context, PlayerWidgetReceiver::class.java, R.layout.player_widget)
        }

        fun updateAllSquare(context: Context) {
            updateWidget(context, PlayerWidgetSquareReceiver::class.java, R.layout.player_widget_square)
        }

        private fun updateWidget(context: Context, providerClass: Class<*>, layoutId: Int) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val provider = ComponentName(appContext, providerClass)
            val ids = manager.getAppWidgetIds(provider)
            if (ids.isEmpty()) return

            val prefs = AppPrefs(appContext)
            val primaryColor = runCatching {
                android.graphics.Color.parseColor(prefs.getPrimaryColor())
            }.getOrDefault(0xFF2196F3.toInt())
            val artwork = prefs.loadLastSongUri()?.let { loadAlbumArt(appContext, it) }
            val views = android.widget.RemoteViews(appContext.packageName, layoutId)

            views.setTextViewText(R.id.widget_title, prefs.loadTitle().ifBlank { appContext.getString(R.string.notification_now_playing) })
            views.setTextViewText(R.id.widget_artist, prefs.loadArtist())
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (prefs.loadIsPlaying()) R.drawable.widget_pause else R.drawable.widget_play,
            )
            artwork?.let { views.setImageViewBitmap(R.id.widget_album_art, it) }
                ?: views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_album)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(
                    R.id.widget_root,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(primaryColor),
                )
            } else {
                views.setInt(R.id.widget_root, "setBackgroundColor", primaryColor)
            }
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(appContext))
            views.setOnClickPendingIntent(R.id.widget_previous, serviceIntent(appContext, MusicService.ACTION_PREV))
            views.setOnClickPendingIntent(R.id.widget_play_pause, serviceIntent(appContext, MusicService.ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next, serviceIntent(appContext, MusicService.ACTION_NEXT))

            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun serviceIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicService::class.java).apply { this.action = action }
            return PendingIntent.getForegroundService(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun loadAlbumArt(context: Context, songUri: String): Bitmap? = runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(songUri))
            val embeddedArt = retriever.embeddedPicture
            retriever.release()
            embeddedArt?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?.let(::scaleAlbumArt)
                ?.let(::roundedAlbumArt)
        }.getOrNull()

        private fun scaleAlbumArt(source: Bitmap): Bitmap {
            val maxSide = 256
            if (source.width <= maxSide && source.height <= maxSide) return source
            val scale = minOf(maxSide.toFloat() / source.width, maxSide.toFloat() / source.height)
            return Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }

        private fun roundedAlbumArt(source: Bitmap): Bitmap {
            val side = minOf(source.width, source.height)
            val left = (source.width - side) / 2
            val top = (source.height - side) / 2
            val rounded = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(rounded)
            val path = Path().apply {
                addRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), side * 0.12f, side * 0.12f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(source, Rect(left, top, left + side, top + side), RectF(0f, 0f, side.toFloat(), side.toFloat()), null)
            canvas.restore()
            return rounded
        }
    }
}
