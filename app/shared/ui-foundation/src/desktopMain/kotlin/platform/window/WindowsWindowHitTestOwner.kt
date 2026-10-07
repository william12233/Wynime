package com.wynime.app.platform.window

fun interface WindowsWindowHitTestOwner {
    fun hitTest(x: Float, y: Float): WindowsWindowHitResult
}

enum class WindowsWindowHitResult(val value: Int) {

    TRANSPARENT(-1),

    NOWHERE(0),

    CLIENT(1),

    CAPTION(2),

    CAPTION_MIN(8),

    CAPTION_MAX(9),

    CAPTION_CLOSE(20),

    BORDER_LEFT(10),
    BORDER_RIGHT(11),
    BORDER_TOP(12),
    BORDER_TOP_LEFT(13),
    BORDER_TOP_RIGHT(14),
    BORDER_BOTTOM(15),
    BORDER_BOTTOM_LEFT(16),
    BORDER_BOTTOM_RIGHT(17);
}