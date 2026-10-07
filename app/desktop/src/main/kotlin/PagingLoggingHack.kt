package com.wynime.app.desktop

import androidx.paging.PagingLogger
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.FixedValue.value
import net.bytebuddy.matcher.ElementMatchers

object PagingLoggingHack {
    fun install() {
        ByteBuddyAgent.install()
        ByteBuddy()
            .redefine(PagingLogger::class.java)
            .method(ElementMatchers.named("isLoggable"))
            .intercept(value(false))
            .make()
            .load(
                PagingLogger::class.java.getClassLoader(),
                ClassReloadingStrategy.fromInstalledAgent(),
            )
    }
}
