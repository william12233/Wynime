package com.wynime.app.data.persistent.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wynime.app.data.models.subject.CharacterInfo

@Entity(
    "character",
)
data class CharacterEntity(
    @PrimaryKey
    val characterId: Int,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
    val imageMedium: String,
)
