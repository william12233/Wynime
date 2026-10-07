package com.wynime.app.data.persistent.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.wynime.app.data.persistent.database.converters.DurationConverter
import com.wynime.app.data.persistent.database.converters.InstantConverter
import com.wynime.app.data.persistent.database.converters.PackedDateConverter
import com.wynime.app.data.persistent.database.dao.EpisodeCollectionDao
import com.wynime.app.data.persistent.database.dao.EpisodeCollectionEntity
import com.wynime.app.data.persistent.database.dao.EpisodeCommentDao
import com.wynime.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import com.wynime.app.data.persistent.database.dao.PlaybackHistoryDao
import com.wynime.app.data.persistent.database.dao.PlaybackHistoryPendingOpEntity
import com.wynime.app.data.persistent.database.dao.PlaybackHistoryRecordEntity
import com.wynime.app.data.persistent.database.dao.PreferredWebMediaSource
import com.wynime.app.data.persistent.database.dao.PreferredWebMediaSourceDao
import com.wynime.app.data.persistent.database.dao.SearchHistoryDao
import com.wynime.app.data.persistent.database.dao.SearchHistoryEntity
import com.wynime.app.data.persistent.database.dao.SearchTagDao
import com.wynime.app.data.persistent.database.dao.SearchTagEntity
import com.wynime.app.data.persistent.database.dao.SubjectCollectionDao
import com.wynime.app.data.persistent.database.dao.SubjectCollectionEntity
import com.wynime.app.data.persistent.database.dao.SubjectRelationsDao
import com.wynime.app.data.persistent.database.dao.SubjectReviewDao
import com.wynime.app.data.persistent.database.dao.BangumiTrackingAccountEntity
import com.wynime.app.data.persistent.database.dao.BangumiTrackingMetadataDao
import com.wynime.app.data.persistent.database.dao.BangumiTrackingMetadataEntity
import com.wynime.app.data.persistent.database.entity.CharacterActorEntity
import com.wynime.app.data.persistent.database.entity.CharacterEntity
import com.wynime.app.data.persistent.database.entity.EpisodeCommentEntity
import com.wynime.app.data.persistent.database.entity.PersonEntity
import com.wynime.app.data.persistent.database.entity.SubjectCharacterRelationEntity
import com.wynime.app.data.persistent.database.entity.SubjectPersonRelationEntity
import com.wynime.app.data.persistent.database.entity.SubjectReviewEntity
import com.wynime.utils.httpdownloader.DownloadState

@Database(
    entities = [
        SearchHistoryEntity::class,
        SearchTagEntity::class,
        SubjectCollectionEntity::class,
        EpisodeCollectionEntity::class,

        PersonEntity::class,
        SubjectPersonRelationEntity::class,

        CharacterEntity::class,
        SubjectCharacterRelationEntity::class,
        CharacterActorEntity::class,

        SubjectReviewEntity::class,
        EpisodeCommentEntity::class,

        DownloadState::class,

        PreferredWebMediaSource::class,
        PlaybackHistoryRecordEntity::class,
        PlaybackHistoryPendingOpEntity::class,
        BangumiTrackingMetadataEntity::class,
        BangumiTrackingAccountEntity::class,
    ],
    version = 31,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = Migrations.Migration_1_2::class),
        AutoMigration(from = 2, to = 3, spec = Migrations.Migration_2_3::class),
        AutoMigration(from = 3, to = 4, spec = Migrations.Migration_3_4::class),
        AutoMigration(from = 4, to = 5, spec = Migrations.Migration_4_5::class),
        AutoMigration(from = 5, to = 6, spec = Migrations.Migration_5_6::class),
        AutoMigration(from = 6, to = 7, spec = Migrations.Migration_6_7::class),
        AutoMigration(from = 7, to = 8, spec = Migrations.Migration_7_8::class),
        AutoMigration(from = 8, to = 9, spec = Migrations.Migration_8_9::class),
        AutoMigration(from = 9, to = 10, spec = Migrations.Migration_9_10::class),
        AutoMigration(from = 10, to = 11, spec = Migrations.Migration_10_11::class),
        AutoMigration(from = 11, to = 12, spec = Migrations.Migration_11_12::class),
        AutoMigration(from = 12, to = 13, spec = Migrations.Migration_12_13::class),
        AutoMigration(from = 13, to = 14, spec = Migrations.Migration_13_14::class),
        AutoMigration(from = 14, to = 15, spec = Migrations.Migration_14_15::class),

        AutoMigration(from = 16, to = 17, spec = Migrations.Migration_16_17::class),
        AutoMigration(from = 17, to = 18, spec = Migrations.Migration_17_18::class),
        AutoMigration(from = 18, to = 19, spec = Migrations.Migration_18_19::class),
        AutoMigration(from = 20, to = 21, spec = Migrations.Migration_20_21::class),
        AutoMigration(from = 21, to = 22, spec = Migrations.Migration_21_22::class),
        AutoMigration(from = 22, to = 23, spec = Migrations.Migration_22_23::class),
        AutoMigration(from = 23, to = 24, spec = Migrations.Migration_23_24::class),
        AutoMigration(from = 24, to = 25, spec = Migrations.Migration_24_25::class),
        AutoMigration(from = 25, to = 26, spec = Migrations.Migration_25_26::class),
        AutoMigration(from = 26, to = 27, spec = Migrations.Migration_26_27::class),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29, spec = Migrations.Migration_28_29::class),
        AutoMigration(from = 29, to = 30, spec = Migrations.Migration_29_30::class),
        AutoMigration(from = 30, to = 31, spec = Migrations.Migration_30_31::class),
    ],
    exportSchema = true,
)
@ConstructedBy(WynimeDatabaseConstructor::class)
@TypeConverters(
    PackedDateConverter::class,
    DurationConverter::class,
    InstantConverter::class,
    EpisodeSortConverter::class,
)
abstract class WynimeDatabase : RoomDatabase() {
    abstract fun searchHistory(): SearchHistoryDao
    abstract fun searchTag(): SearchTagDao
    abstract fun subjectCollection(): SubjectCollectionDao
    abstract fun episodeCollection(): EpisodeCollectionDao

    abstract fun subjectRelations(): SubjectRelationsDao

    abstract fun subjectReviews(): SubjectReviewDao

    abstract fun episodeCommentDao(): EpisodeCommentDao

    abstract fun httpCacheDownloadStateDao(): HttpCacheDownloadStateDao

    abstract fun preferredWebMediaSourceDao(): PreferredWebMediaSourceDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun bangumiTrackingMetadataDao(): BangumiTrackingMetadataDao
}

expect object WynimeDatabaseConstructor : RoomDatabaseConstructor<WynimeDatabase> {
    override fun initialize(): WynimeDatabase
}

val MIGRATION_19_20 = object : Migration(startVersion = 19, endVersion = 20) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `episode_comment`")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `episode_comment` (
                `episodeId` INTEGER NOT NULL,
                `commentId` TEXT NOT NULL,
                `authorId` TEXT NOT NULL,
                `parentCommentId` TEXT,
                `authorNickname` TEXT NOT NULL,
                `authorAvatarUrl` TEXT,
                `createdAt` INTEGER NOT NULL,
                `content` TEXT NOT NULL,
                PRIMARY KEY(`commentId`),
                FOREIGN KEY(`episodeId`) REFERENCES `episode_collection`(`episodeId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`parentCommentId`) REFERENCES `episode_comment`(`commentId`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_episode_comment_episodeId` ON `episode_comment` (`episodeId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_episode_comment_parentCommentId` ON `episode_comment` (`parentCommentId`)",
        )
    }
}

@Suppress("ClassName")
internal object Migrations {

    class Migration_1_2 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_2_3 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `distinct_content` ON `search_history`(`content`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `sequence_desc` ON `search_history`(`sequence` DESC)")
        }
    }

    class Migration_3_4 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    @RenameTable("related_character", "character_actor")
    class Migration_4_5 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_5_6 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    @DeleteColumn("subject_collection", "_index")
    class Migration_6_7 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_7_8 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    @RenameColumn("episode_collection", "lastUpdated", "lastFetched")
    class Migration_8_9 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_9_10 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_10_11 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_11_12 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_12_13 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_13_14 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_14_15 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_16_17 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_17_18 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_18_19 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_20_21 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    @DeleteTable("web_search_episode")
    @DeleteTable("web_search_subject")
    class Migration_21_22 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_22_23 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_23_24 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_24_25 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_25_26 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    @DeleteTable("torrent_cache")
    @DeleteTable("torrent_cache_episode")
    @DeleteTable("danmaku")
    class Migration_26_27 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    @DeleteTable("web_search_session_cache")
    class Migration_28_29 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_29_30 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    class Migration_30_31 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }
}
