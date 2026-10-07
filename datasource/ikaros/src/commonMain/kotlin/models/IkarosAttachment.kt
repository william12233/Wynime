package models

import kotlinx.serialization.Serializable
import com.wynime.datasources.ikaros.models.IkarosAttachmentType

@Serializable
class IkarosAttachment(
    val id: Long = 0,
    val parentId: Long = 0,
    val type: IkarosAttachmentType = IkarosAttachmentType.File,

    val url: String? = null,

    val path: String? = null,

    val fsPath: String? = null,

    val name: String? = null,
    val size: Long = 0,
    val updateTime: String? = null,
)
