package com.wynime.app.domain.mediasource.web.captcha

import com.wynime.app.data.Res

internal const val IMAGE_CAPTCHA_MODEL_RESOURCE = "files/captcha-v1.0.onnx"

suspend fun readImageCaptchaModelBytes(): ByteArray = Res.readBytes(IMAGE_CAPTCHA_MODEL_RESOURCE)

fun imageCaptchaModelUri(): String = Res.getUri(IMAGE_CAPTCHA_MODEL_RESOURCE)
