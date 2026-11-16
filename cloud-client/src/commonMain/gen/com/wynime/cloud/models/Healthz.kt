@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.cloud.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class Healthz (

    @SerialName(value = "ok") @Required val ok: kotlin.Boolean,

    @SerialName(value = "serverTimeMillis") @Required val serverTimeMillis: kotlin.Long,

    @SerialName(value = "apiVersion") @Required val apiVersion: kotlin.Int

) {

}
