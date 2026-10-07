package com.wynime.app.data.persistent.database

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import com.wynime.app.data.models.subject.SubjectTmdbArt
import com.wynime.app.data.models.subject.Tag
import com.wynime.datasources.api.EpisodeSort
import com.wynime.utils.serialization.DatabaseProtoBuf

interface ProtoConverter<T> {
    @TypeConverter
    fun fromByteArray(value: ByteArray): T

    @TypeConverter
    fun fromList(list: T): ByteArray
}

object ProtoConverters {
    class StringList {
        @Serializable
        private class Node(val value: List<String>)

        @TypeConverter
        fun fromByteArray(value: ByteArray): List<String> {
            return DatabaseProtoBuf.decodeFromByteArray(Node.serializer(), value).value
        }

        @TypeConverter
        fun fromList(list: List<String>): ByteArray {
            return DatabaseProtoBuf.encodeToByteArray(Node.serializer(), Node(list))
        }
    }

    object IntList : ProtoConverter<List<Int>> {
        @Serializable
        private class Node(val value: List<Int>)

        @TypeConverter
        override fun fromByteArray(value: ByteArray): List<Int> {
            return DatabaseProtoBuf.decodeFromByteArray(Node.serializer(), value).value
        }

        @TypeConverter
        override fun fromList(list: List<Int>): ByteArray {
            return DatabaseProtoBuf.encodeToByteArray(Node.serializer(), Node(list))
        }
    }

    object TagList : ProtoConverter<List<Tag>> {
        @Serializable
        private class Node(val value: List<Tag>)

        @TypeConverter
        override fun fromByteArray(value: ByteArray): List<Tag> {
            return DatabaseProtoBuf.decodeFromByteArray(Node.serializer(), value).value
        }

        @TypeConverter
        override fun fromList(list: List<Tag>): ByteArray {
            return DatabaseProtoBuf.encodeToByteArray(Node.serializer(), Node(list))
        }
    }

    object SubjectTmdbArtConverter {
        @TypeConverter
        fun fromByteArray(value: ByteArray?): SubjectTmdbArt? {
            return value?.let { DatabaseProtoBuf.decodeFromByteArray(SubjectTmdbArt.serializer(), it) }
        }

        @TypeConverter
        fun fromSubjectTmdbArt(art: SubjectTmdbArt?): ByteArray? {
            return art?.let { DatabaseProtoBuf.encodeToByteArray(SubjectTmdbArt.serializer(), it) }
        }
    }

    object IntArrayConverter : ProtoConverter<IntArray> {
        @Serializable
        private class Node(val value: IntArray)

        @TypeConverter
        override fun fromByteArray(value: ByteArray): IntArray {
            return DatabaseProtoBuf.decodeFromByteArray(Node.serializer(), value).value
        }

        @TypeConverter
        override fun fromList(list: IntArray): ByteArray {
            return DatabaseProtoBuf.encodeToByteArray(Node.serializer(), Node(list))
        }
    }
}

class EpisodeSortConverter {
    @TypeConverter
    fun fromString(value: String): EpisodeSort {
        return EpisodeSort(value)
    }

    @TypeConverter
    fun fromEpisodeSort(sort: EpisodeSort): String {
        return sort.toString()
    }
}

