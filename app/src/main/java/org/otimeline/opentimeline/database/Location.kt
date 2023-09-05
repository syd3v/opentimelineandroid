package org.otimeline.opentimeline.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * The base class used for creating the Location objects table
 *
 * This class is known as the data entity class
 * https://developer.android.com/training/data-storage/room/defining-data
 */

@Entity(tableName = "location_table")
data class Location(

    // Primary key usually incrementing numbers
    @PrimaryKey(autoGenerate = true) val recordId: Long = 0L,

    // Records the epoch time (in milliseconds since 1970 which can easily be converted to a date)
    @ColumnInfo(name = "record_time") val recordTime: Long = System.currentTimeMillis(),

    // Records records latitude
    @ColumnInfo(name = "latitude") val latitude: Double?,

    // Records records longitude
    @ColumnInfo(name = "longitude") val longitude: Double?
)