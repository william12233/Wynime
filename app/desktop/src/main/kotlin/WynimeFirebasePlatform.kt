package com.wynime.app.desktop

import androidx.datastore.core.DataStore
import com.google.firebase.FirebasePlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.wynime.utils.logging.logger

class WynimeFirebasePlatform(
    private val datastore: DataStore<Map<String, String>>,

) : FirebasePlatform() {
    private val logger = logger<WynimeFirebasePlatform>()

    override fun store(key: String, value: String) {
        runBlocking {
            datastore.updateData {
                it + (key to value)
            }
        }
    }

    override fun retrieve(key: String) = runBlocking {
        datastore.data.first()[key]
    }

    override fun clear(key: String) {
        runBlocking {
            datastore.updateData {
                it - key
            }
        }
    }

    override fun log(msg: String) = logger.info(msg)
}
