package com.wynime.app.ui.media

import androidx.compose.runtime.Composable
import com.wynime.app.domain.mediasource.web.WebCaptchaKind
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.media_captcha_cloudflare
import com.wynime.app.ui.lang.media_captcha_image
import com.wynime.app.ui.lang.media_captcha_required_message
import com.wynime.app.ui.lang.media_captcha_slider
import com.wynime.app.ui.lang.media_captcha_turnstile
import com.wynime.app.ui.lang.media_captcha_unknown
import org.jetbrains.compose.resources.stringResource

@Composable
fun webCaptchaRequiredMessage(kind: WebCaptchaKind): String {
    val name = stringResource(when (kind) {
        WebCaptchaKind.Image -> Lang.media_captcha_image
        WebCaptchaKind.Cloudflare -> Lang.media_captcha_cloudflare
        WebCaptchaKind.CloudflareTurnstile -> Lang.media_captcha_turnstile
        WebCaptchaKind.SliderCaptcha -> Lang.media_captcha_slider
        WebCaptchaKind.Unknown -> Lang.media_captcha_unknown
    })
    return stringResource(Lang.media_captcha_required_message, name)
}
