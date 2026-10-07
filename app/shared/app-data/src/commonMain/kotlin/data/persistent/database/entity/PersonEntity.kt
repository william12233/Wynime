package com.wynime.app.data.persistent.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wynime.app.data.models.subject.PersonType

@Entity(
    "person",
)
data class PersonEntity(
    @PrimaryKey
    val personId: Int = 0,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
    val imageMedium: String,
    val type: PersonType,
    val summary: String,
)
