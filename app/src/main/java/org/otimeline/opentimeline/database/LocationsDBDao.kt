package org.otimeline.opentimeline.database


import androidx.room.*

/**
 * The Data Access Object interface class is used to interact with the
 * data in the database. In this case only a single table is being interacted
 * with
 *
 * While there are some dedicated annotation/tags, using @Query allows for direct
 * interacting with the SQLite database Rooms runs on
 */

@Dao
interface LocationsDBDao {

    // Inserts a new record
    @Insert
    fun insertLocation(location: Location)

    // Queries the database for the latest object
    @Query("SELECT * FROM location_table ORDER BY record_time DESC LIMIT 1")
    fun retrieveLatestLocation(): Location?

    // Function to retrieve all records from the table
    @Query("SELECT * FROM location_table ORDER BY record_time ASC")
    fun retrieveAllRecords(): List<Location>

    /**
     * Function to retrieve all records recorded between a certain time
     * @startTime The time from which records should be retrieved
     * @endTime The time until which records should be retrieved
     * @return List of locations if any retrieved
     */
    @Query("SELECT * FROM location_table WHERE record_time BETWEEN :startTime AND :endTime")
    fun retrieveForDate(startTime: Long, endTime: Long): List<Location>

    // Clear the whole table
    @Query("DELETE FROM location_table")
    fun clearTable()

}