package com.wynime.app.ui.settings.rendering

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.wynime.app.data.network.protocol.ReleaseClass

@Composable
fun ReleaseClassIcon(releaseClass: ReleaseClass, modifier: Modifier = Modifier) {
    when (releaseClass) {
        ReleaseClass.ALPHA -> Icon(Icons.Outlined.RocketLaunch, null, modifier)
        ReleaseClass.BETA -> Icon(Icons.Outlined.Science, null, modifier)
        ReleaseClass.RC, ReleaseClass.STABLE -> Icon(Icons.Outlined.Verified, null, modifier)
    }
}

@Stable
fun guessReleaseClass(version: String): ReleaseClass {
    val metadata = version.substringAfter("-", "").lowercase()
    return when {
        metadata.isEmpty() -> ReleaseClass.STABLE
        "alpha" in metadata || "dev" in metadata -> ReleaseClass.ALPHA
        "beta" in metadata -> ReleaseClass.BETA
        "rc" in metadata -> ReleaseClass.RC
        else -> ReleaseClass.STABLE
    }
}
