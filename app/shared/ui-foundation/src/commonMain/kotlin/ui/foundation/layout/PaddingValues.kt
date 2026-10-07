package com.wynime.app.ui.foundation.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.layout.PaddingValuesSides.Companion.Bottom
import com.wynime.app.ui.foundation.layout.PaddingValuesSides.Companion.End
import com.wynime.app.ui.foundation.layout.PaddingValuesSides.Companion.Left
import com.wynime.app.ui.foundation.layout.PaddingValuesSides.Companion.Right
import com.wynime.app.ui.foundation.layout.PaddingValuesSides.Companion.Start
import com.wynime.app.ui.foundation.layout.PaddingValuesSides.Companion.Top

@Stable
fun PaddingValues.only(sides: PaddingValuesSides): PaddingValues = OnlyPaddingValues(this, sides)

private class OnlyPaddingValues(
    private val delegate: PaddingValues,
    private val sides: PaddingValuesSides
) : PaddingValues {
    override fun calculateBottomPadding(): Dp {
        if (sides.hasAny(PaddingValuesSides.Bottom)) {
            return delegate.calculateBottomPadding()
        }
        return 0.dp
    }

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
        if (layoutDirection == LayoutDirection.Ltr) {
            if (sides.hasAny(PaddingValuesSides.AllowLeftInLtr)) {
                return delegate.calculateLeftPadding(layoutDirection)
            }
        } else {
            if (sides.hasAny(PaddingValuesSides.AllowLeftInRtl)) {
                return delegate.calculateLeftPadding(layoutDirection)
            }
        }
        return 0.dp
    }

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
        if (layoutDirection == LayoutDirection.Ltr) {
            if (sides.hasAny(PaddingValuesSides.AllowRightInLtr)) {
                return delegate.calculateLeftPadding(layoutDirection)
            }
        } else {
            if (sides.hasAny(PaddingValuesSides.AllowRightInRtl)) {
                return delegate.calculateLeftPadding(layoutDirection)
            }
        }
        return 0.dp
    }

    override fun calculateTopPadding(): Dp {
        if (sides.hasAny(PaddingValuesSides.Top)) {
            return delegate.calculateTopPadding()
        }
        return 0.dp
    }
}

@kotlin.jvm.JvmInline
value class PaddingValuesSides private constructor(private val value: Int) {

    operator fun plus(sides: PaddingValuesSides): PaddingValuesSides =
        PaddingValuesSides(value or sides.value)

    internal fun hasAny(sides: PaddingValuesSides): Boolean =
        (value and sides.value) != 0

    override fun toString(): String = "PaddingValuesSides(${valueToString()})"

    private fun valueToString(): String = buildString {
        fun appendPlus(text: String) {
            if (isNotEmpty()) append('+')
            append(text)
        }

        if (value and Start.value == Start.value) appendPlus("Start")
        if (value and Left.value == Left.value) appendPlus("Left")
        if (value and Top.value == Top.value) appendPlus("Top")
        if (value and End.value == End.value) appendPlus("End")
        if (value and Right.value == Right.value) appendPlus("Right")
        if (value and Bottom.value == Bottom.value) appendPlus("Bottom")
    }

    companion object {

        internal val AllowLeftInLtr = PaddingValuesSides(1 shl 3)
        internal val AllowRightInLtr = PaddingValuesSides(1 shl 2)
        internal val AllowLeftInRtl = PaddingValuesSides(1 shl 1)
        internal val AllowRightInRtl = PaddingValuesSides(1 shl 0)

        val Start = AllowLeftInLtr + AllowRightInRtl

        val End = AllowRightInLtr + AllowLeftInRtl

        val Top = PaddingValuesSides(1 shl 4)

        val Bottom = PaddingValuesSides(1 shl 5)

        val Left = AllowLeftInLtr + AllowLeftInRtl

        val Right = AllowRightInLtr + AllowRightInRtl

        val Horizontal = Left + Right

        val Vertical = Top + Bottom
    }
}

operator fun PaddingValues.plus(
    other: PaddingValues,
): PaddingValues {
    val first = this
    return object : PaddingValues {
        override fun calculateBottomPadding(): Dp = first.calculateBottomPadding() + other.calculateBottomPadding()

        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
            return first.calculateLeftPadding(layoutDirection) + other.calculateLeftPadding(layoutDirection)
        }

        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
            return first.calculateRightPadding(layoutDirection) + other.calculateRightPadding(layoutDirection)
        }

        override fun calculateTopPadding(): Dp {
            return first.calculateTopPadding() + other.calculateTopPadding()
        }
    }
}
