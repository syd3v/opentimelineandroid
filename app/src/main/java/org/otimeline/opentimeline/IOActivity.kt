package org.otimeline.opentimeline

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.room.Room
import org.otimeline.opentimeline.database.*
import org.otimeline.opentimeline.io.*

/**
 * The IOActivity allows the user to manage their data in the database
 *
 * It gives the user options to import, export, as well as clear their data
 *
 * If debug is enabled, an additional button allowing for test data to be
 * added to the DB is shown
 */

class IOActivity : AppCompatActivity() {

    // Class variables
    private lateinit var addRecords: Button
    private lateinit var exportGPX: Button
    private lateinit var importGPX: Button
    private lateinit var clearData: Button
    private val CREATE_FILE = 1
    private val RETRIEVE_FILE = 2
    private val debug = false

    // Database variables
    private lateinit var db: LocationsDB
    private lateinit var locationDao: LocationsDBDao

    // Variable to interact with toolbar and buttons
    private lateinit var actionBar: ActionBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.io_activity_layout)

        // Create the database (or just database reference) and database DAO
        // This builder also forces the use of main thread queries, which is not recommended
        db = Room.databaseBuilder(
            applicationContext,
            LocationsDB::class.java,
            "locationsDb"
        ).allowMainThreadQueries().build()
        locationDao = db.locationsDao()

        // Initiate the export to GPX button
        exportGPX = findViewById(R.id.export_gpx)
        exportGPX.setOnClickListener {
            toGPX("locations.gpx")
        }
        exportGPX.setOnLongClickListener {
            Toast.makeText(applicationContext, R.string.on_long_export_click_message, Toast.LENGTH_LONG).show()
            true
        }

        // Initiate the import from GPX button
        importGPX = findViewById(R.id.import_gpx)
        importGPX.setOnClickListener {
            fromGPX()
        }
        importGPX.setOnLongClickListener {
            Toast.makeText(applicationContext, R.string.on_long_click_import_message, Toast.LENGTH_LONG).show()
            true
        }

        // Initiate the button allowing the user to clear the database.
        // The user is first asked to confirm they want to go ahead before the database is cleared.
        clearData = findViewById(R.id.clear_db)
        clearData.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.alert)
                .setMessage(R.string.sure_want_delete_message)
                .setPositiveButton(R.string.yes) { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.dismiss()
                    locationDao.clearTable()
                    Toast.makeText(this, R.string.data_cleared_message, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel) { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.dismiss()
                    Toast.makeText(this, R.string.data_not_cleared_message, Toast.LENGTH_LONG).show()
                }
                .show()
        }
        clearData.setOnLongClickListener {
            Toast.makeText(applicationContext, R.string.on_long_click_clear_data_message, Toast.LENGTH_LONG).show()
            true
        }

        // The add records button is for testing and will only be functional if debug is set to true
        addRecords = findViewById(R.id.add_records)
        if (debug) {
            addRecords.setOnClickListener {
                addRecords()
            }
        } else {
            addRecords.visibility = View.INVISIBLE
        }

        // Set up the action bar
        setSupportActionBar(findViewById(R.id.toolbar))
        actionBar = supportActionBar!!
        actionBar.displayOptions
        actionBar.setDisplayHomeAsUpEnabled(true)

    }

    // Overrides when the back button is pressed so it goes to the main activity
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Adds 1000 dummy records to the database between the dates of Jan and Aug for testing
    private fun addRecords() {

        val firstJan = 1672531200000
        val firstAug = 1690848000000
        val maxLatLon = 500
        val minLatLon = -500

        for(i in 1..1000) {
            val timeStamp = (firstJan..firstAug).random()
            val lat: Double = (minLatLon..maxLatLon).random().toDouble() / 10
            val lon: Double = (minLatLon..maxLatLon).random().toDouble() / 10

            val location = Location(recordTime = timeStamp, latitude = lat, longitude = lon)
            locationDao.insertLocation(location)
        }

        Toast.makeText(applicationContext, R.string.test_records_inserted, Toast.LENGTH_SHORT).show()
    }

    // Requests the user to select a folder and filename, which is
    // returned to the onActivityResult function
    private fun toGPX(filename: String) {

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/gpx"
            putExtra(Intent.EXTRA_TITLE, filename)
        }

        startActivityForResult(intent, CREATE_FILE)
    }

    // Requests the user select a file, which is returned to the
    // onActivityResult function
    private fun fromGPX() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }

        startActivityForResult(intent, RETRIEVE_FILE)
    }


    // Receives the users specified document address, and then either starts the import
    // or export class depending on the received code
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CREATE_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { documentUri ->
                LocOutputGPX.writeLocationsToGPX(locationDao.retrieveAllRecords(), documentUri, applicationContext)
                Toast.makeText(applicationContext, R.string.data_exported_message, Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == RETRIEVE_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                val locations: List<Location> = SAXInputGPX.readLocationsFromGPX(uri, applicationContext)
                if (!locations.isEmpty()) {
                    for (location in locations) {
                        locationDao.insertLocation(
                            Location(
                                recordTime = location.recordTime,
                                latitude = location.latitude,
                                longitude = location.longitude
                            )
                        )
                    }
                    Toast.makeText(applicationContext, R.string.data_imported_message, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }
}