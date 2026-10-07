package com.wynime.datasources.ikaros.models

import kotlinx.serialization.Serializable

@Serializable
enum class IkarosAttachmentType {
    File,
    Directory,
    Driver_File,
    Driver_Directory;
}
