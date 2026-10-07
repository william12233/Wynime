package com.wynime.android.provider

import android.app.Application
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.ui.exprovider.ExternalContentProviderFactory
import com.wynime.app.ui.exprovider.NoOpExternalContentProviderFactory

@Suppress("LocalVariableName")
class ExternalContentProviderFactoryImpl(_unused: UserRepository) :
    ExternalContentProviderFactory by NoOpExternalContentProviderFactory {

    companion object {
        fun initializeApp(application: Application) = Unit
    }
}