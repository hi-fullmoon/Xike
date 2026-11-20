package com.xike.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.data.model.LocationSource
import com.xike.app.outdoor.OutdoorContextRepository
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OutdoorSourcePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            JournalDatabase::class.java,
        )

    @Test
    fun schemaEightUpgradePreservesWeatherAndDoesNotGuessItsLocationProvider() {
        val name = "location-source-migration-${UUID.randomUUID()}"
        try {
            helper.createDatabase(name, 8).use { db ->
                db.execSQL(
                    "INSERT INTO journal_entries(id,created_at,mood,note,outdoor_place_name,outdoor_temperature_celsius,outdoor_weather_code,outdoor_captured_at,outdoor_source) VALUES('legacy',123,'CALM','','杭州',20,1,123,'Open-Meteo')"
                )
            }
            helper.runMigrationsAndValidate(name, 9, true, JournalDatabase.MIGRATION_8_9).close()
            val db = Room.databaseBuilder(context, JournalDatabase::class.java, name).build()
            try {
                val restored = db.journalDao().record("legacy")!!.toJournalEntry()
                val snapshot = requireNotNull(restored.outdoor)
                assertEquals("杭州", snapshot.placeName)
                assertEquals("Open-Meteo", snapshot.source)
                assertNull(snapshot.locationSource)
                assertEquals("", restored.note)
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    /** Explicit isolated database only: write/read arguments exercise different app processes. */
    @Test
    fun encryptedLocationSourceSurvivesProcessRestart() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("outdoorSourcePhase")
        val name =
            args.getString("outdoorSourceDatabase")
                ?: "location-source-verification-${UUID.randomUUID()}"
        require(name.matches(Regex("location-source-verification-[a-zA-Z0-9-]+")))
        when (phase) {
            "write" -> writeSource(name)
            "read" -> readSource(name, true)
            null -> {
                writeSource(name)
                readSource(name, false)
            }
            else -> error("Unsupported persistence phase")
        }
    }

    private fun database(name: String): JournalDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, JournalDatabase::class.java, name)
            .openHelperFactory(
                SupportOpenHelperFactory(DatabaseKeyManager(context).getOrCreatePassphrase())
            )
            .build()
    }

    private fun writeSource(name: String) {
        // Real public-city weather through the production manual-selection path; no SDK result
        // is synthesized and no user journal or draft database is opened.
        val snapshot = runBlocking { OutdoorContextRepository(context).city("杭州") }
        assertEquals(LocationSource.MANUAL, snapshot.locationSource)
        val db = database(name)
        try {
            db.journalDao()
                .insertJournal(
                    JournalEntry(
                            id = "provider-round-trip",
                            mood = Mood.CALM,
                            tags = emptyList(),
                            note = "",
                            outdoor = snapshot,
                        )
                        .toBundle()
                )
            assertTrue(
                context
                    .getSharedPreferences("$name-meta", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("writer", android.os.Process.myPid())
                    .commit()
            )
        } finally {
            db.close()
        }
    }

    private fun readSource(name: String, differentProcess: Boolean) {
        try {
            val writer =
                context
                    .getSharedPreferences("$name-meta", Context.MODE_PRIVATE)
                    .getInt("writer", -1)
            assertTrue(writer > 0)
            if (differentProcess) assertNotEquals(writer, android.os.Process.myPid())
            val db = database(name)
            try {
                val snapshot =
                    db.journalDao().record("provider-round-trip")!!.toJournalEntry().outdoor!!
                assertEquals(LocationSource.MANUAL, snapshot.locationSource)
                assertEquals("Open-Meteo", snapshot.source)
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
            context.deleteSharedPreferences("$name-meta")
        }
    }
}
