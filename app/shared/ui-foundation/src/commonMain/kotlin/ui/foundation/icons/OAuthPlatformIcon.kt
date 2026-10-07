package com.wynime.app.ui.foundation.icons

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wynime.app.domain.session.auth.OAuthPlatform

@Composable
fun OAuthPlatformIcon(
    platform: OAuthPlatform,
    modifier: Modifier = Modifier,
) {
    when (platform) {
        OAuthPlatform.BANGUMI -> Image(Icons.Default.BangumiNext, contentDescription = platform.displayName, modifier)
    }
}
