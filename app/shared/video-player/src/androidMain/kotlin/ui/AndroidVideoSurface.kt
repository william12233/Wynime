package com.wynime.app.videoplayer.ui

import android.view.SurfaceView
import org.openani.mediamp.MediampPlayer
import java.lang.ref.WeakReference
import java.util.WeakHashMap

private val videoSurfaces = WeakHashMap<MediampPlayer, WeakReference<SurfaceView>>()

internal fun registerAndroidVideoSurface(player: MediampPlayer, surfaceView: SurfaceView) {
    synchronized(videoSurfaces) {
        videoSurfaces[player] = WeakReference(surfaceView)
    }
}

fun MediampPlayer.findAndroidVideoSurface(): SurfaceView? = synchronized(videoSurfaces) {
    videoSurfaces[this]?.get()
}
