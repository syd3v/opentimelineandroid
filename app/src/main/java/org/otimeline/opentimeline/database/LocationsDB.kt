package org.otimeline.opentimeline.database


import androidx.room.*
import androidx.room.RoomDatabase

/**
 * Database class which implements the database and acts as the entry
 * point for the rest of the app.
 *
 */

@Database(entities = [Location::class], version = 1, exportSchema = false)
abstract class LocationsDB : RoomDatabase() {
    abstract fun locationsDao() : LocationsDBDao
}