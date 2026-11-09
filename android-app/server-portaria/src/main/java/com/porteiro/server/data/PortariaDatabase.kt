package com.porteiro.server.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Apartment::class,
        Resident::class,
        Device::class,
        FacialEmbedding::class,
        AccessLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PortariaDatabase : RoomDatabase() {
    abstract fun apartmentDao(): ApartmentDao
    abstract fun residentDao(): ResidentDao
    abstract fun deviceDao(): DeviceDao
    abstract fun facialEmbeddingDao(): FacialEmbeddingDao
    abstract fun accessLogDao(): AccessLogDao

    companion object {
        @Volatile
        private var instance: PortariaDatabase? = null

        fun get(context: Context): PortariaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PortariaDatabase::class.java,
                    "porteiro.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
