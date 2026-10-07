package com.wynime.app.data.persistent.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    "character_actor",
    indices = [
        Index(value = ["characterId", "actorPersonId"], unique = true),
        Index(value = ["actorPersonId"], orders = [Index.Order.ASC]),
    ],
    foreignKeys = [
        ForeignKey(
            CharacterEntity::class,
            parentColumns = ["characterId"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            PersonEntity::class,
            parentColumns = ["personId"],
            childColumns = ["actorPersonId"],
            onDelete = ForeignKey.RESTRICT,
            deferred = true,
        ),
    ],
)
data class CharacterActorEntity(
    @PrimaryKey
    val characterId: Int,
    val actorPersonId: Int,
)