package io.github.william12233.wynime

import android.content.Context
import android.view.View
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

internal class Media3PlayerPlatformView(
    context: Context,
    private val activity: MainActivity,
) : PlatformView {
    private val playerView = PlayerView(context)

    init {
        activity.registerMedia3View(this)
        rebind(activity.attachedMedia3Player())
    }

    override fun getView(): View = playerView

    override fun dispose() {
        activity.unregisterMedia3View(this)
        playerView.player = null
    }

    fun rebind(player: Player?) {
        playerView.player = player
    }
}

internal class Media3PlayerPlatformViewFactory(
    private val activity: MainActivity,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(
        context: Context,
        viewId: Int,
        args: Any?,
    ): PlatformView = Media3PlayerPlatformView(context, activity)
}
