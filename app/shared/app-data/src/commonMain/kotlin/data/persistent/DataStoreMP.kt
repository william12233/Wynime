package com.wynime.app.data.persistent

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import com.wynime.utils.io.SystemPath

expect interface DataStoreSerializer<T>

expect fun <T> KSerializer<T>.asDataStoreSerializer(
    defaultValue: () -> T,
    format: Json = DataStoreJson,
): DataStoreSerializer<T>

expect fun <T> ReplaceFileCorruptionHandler(produceNewData: (CorruptionException) -> T): ReplaceFileCorruptionHandler<T>

fun <T> DataStoreFactory.create(
    serializer: KSerializer<T>,
    defaultValue: () -> T,
    corruptionHandler: ReplaceFileCorruptionHandler<T>?,
    migrations: List<DataMigration<T>> = listOf(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> SystemPath
): DataStore<T> {
    return create(
        serializer = serializer.asDataStoreSerializer(defaultValue),
        corruptionHandler = corruptionHandler,
        migrations = migrations,
        scope = scope,
        produceFile = produceFile,
    )
}

expect fun <T> DataStoreFactory.create(
    serializer: DataStoreSerializer<T>,
    corruptionHandler: ReplaceFileCorruptionHandler<T>?,
    migrations: List<DataMigration<T>> = listOf(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> SystemPath
): DataStore<T>

val DataStoreJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
    allowSpecialFloatingPointValues = true
}
