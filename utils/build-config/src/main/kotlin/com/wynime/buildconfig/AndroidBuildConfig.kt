package com.wynime.buildconfig

object AndroidBuildConfig {
    val DEBUG: Boolean
        get() = BuildConfig.DEBUG

    val APP_APPLICATION_ID: String
        get() = BuildConfig.APP_APPLICATION_ID
}