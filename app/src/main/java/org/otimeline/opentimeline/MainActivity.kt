package org.otimeline.opentimeline

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.maps.model.LatLng
import java.time.LocalDate
import java.util.Calendar
import org.otimeline.opentimeline.database.*
import org.otimeline.opentimeline.services.*
import org.otimeline.opentimeline.fragments.*
import java.sql.Date
import java.sql.Time
import java.time.ZoneOffset

/**
 * This is the main activity of the Open Timeline app
 *
 * It implements the activity_main.xml interface, as well as the
 * main_action_bar.xml interface for its action bar
 *
 * The main activity takes care of displaying a map, and allowing the
 * user to use the pop up calender to choose a days records
 *
 * A request for permissions is also made from this class if not already
 * granted
 */

class MainActivity : AppCompatActivity(), InterfaceUtils {

    // Logging tag
    private val TAG = "MainActivity"

    // Initialising the variable to access the map fragment
    private var mMap: MapFragment = MapFragment(this)

    // Variable to interact with toolbar and buttons
    private lateinit var actionBar: ActionBar

    // Create database and database DAO variables
    private lateinit var db: LocationsDB
    private lateinit var locationDao: LocationsDBDao

    // Button and object used to present the user with a calendar dialog
    private var date: LocalDate = LocalDate.now()
    private var cal = Calendar.getInstance()

    // Variables for shared preferences
    private val IS_TRACKING = "tracking"
    private val SETTINGS = "settings"
    private val NOPERMISSIONREQUEST = "noPermissionRequest"
    private val WORK_ID = "scheduledTask"

    override fun onCreate(savedInstanceState: Bundle?) {

        Log.i(TAG, "onCreate called")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initiate the shared preference interface
        val prefs = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

        // Checks if precise location, background location, and notifications have been enabled.
        // It also checks if the user has asked not to have permissions requested
        if (((ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED)) &&
                !prefs.getBoolean(NOPERMISSIONREQUEST, false)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_request)
                .setMessage(R.string.permission_dialog)
                .setPositiveButton(R.string.ok) { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.dismiss()
                    requestPermissions(1)
                }
                .setNeutralButton(R.string.cancel) { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.dismiss()
                    Toast.makeText(this, R.string.timeline_wont_work, Toast.LENGTH_LONG).show()
                }
                .setNegativeButton(R.string.stop_showing) { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.dismiss()
                    prefs.edit().putBoolean(NOPERMISSIONREQUEST, true).apply()
                    Toast.makeText(this, R.string.timeline_wont_work, Toast.LENGTH_LONG).show()
                }
                .show()
        }

        // Set up the map fragment
        Log.i(TAG, "Initialising MapFragment")
        supportFragmentManager.beginTransaction()
            .replace(R.id.map_fragment_container, mMap)
            .commit()

        // Create the database (or just database reference) and database DAO
        // This builder also forces the use of main thread queries, which is not recommended
        db = Room.databaseBuilder(
            applicationContext,
            LocationsDB::class.java, "locationsDb"
        ).allowMainThreadQueries().build()
        locationDao = db.locationsDao()

        // Set up the action bar
        setSupportActionBar(findViewById(R.id.toolbar))
        actionBar = supportActionBar!!
        actionBar.displayOptions

        // Find out if tracking is enabled
        val startTracking = prefs?.getBoolean(IS_TRACKING, false)

        // Starts up the tracking service if the user has set indicated they want it running
        if (startTracking == true) {

            Log.i(TAG, "Tracking is true, attempting to start tracking")

            // Cancels all tasks with the task and starts a new worker chain
            WorkManager.getInstance(applicationContext).cancelAllWorkByTag(WORK_ID)

            val locationWorker = OneTimeWorkRequestBuilder<LocationWorker>()
                .addTag(WORK_ID)
                .build()
            WorkManager.getInstance(applicationContext).enqueue(locationWorker)
        } else {
            Log.i(TAG, "Tracking is false, not starting tracking")
        }
    }

    // requestPermissions is used to request all the permissions needed, because of the way Android handles this,
    // it is achieved in multiple stages, (using an int to signify the stage being requested)
    // (If a permission has already been given, Android will skip to the next section)
    @SuppressLint("BatteryLife")
    fun requestPermissions(pass: Int) {
        if (pass == 1) {
            // In SDK 33+, you also have to request notification access permissions from the user
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.i(TAG, "Requesting Notification, Fine and Course location permissions")
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                Log.i(TAG, "Requesting Fine and Course location permissions")
                // This requests the permissions whenever the app is launched if they have not already been requested
                // It also requests the notification permission from the user, as it is off by default in Android 13
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else if (pass == 2){
            Log.i(TAG, "Asking for background location permission")
            Toast.makeText(this, R.string.always_allow_dialog, Toast.LENGTH_LONG).show()

            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            )
        } else if (pass == 3) {
            Log.i(TAG, "Asking for System alert permission")

            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName))
            startActivity(intent)
        }
    }


    // Overriding the menu method so it is pointing at the custom menu layout file
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_action_bar, menu)

        return true
    }

    // Set up all the menu buttons actions, set up using this guide:
    // https://developer.android.com/develop/ui/views/components/appbar/actions
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.manage_data -> {
            requestPermissions(3)
            val intent = Intent(this, IOActivity::class.java)
            startActivity(intent)
            true
        }

        R.id.getCal -> {
            DatePickerDialog(this@MainActivity, dateSetListener,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show()

            true
        }

        R.id.action_settings -> {
            val intent = Intent(this, Settings::class.java)
            startActivity(intent)
            true
        }

        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    // Added following this guide: https://developer.android.com/training/permissions/requesting#request-permission
    // This function is called by onCreate and asks for the location permissions if not already granted
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted. Now background location is needed
                requestPermissions(2)
            }

            permissions.getOrDefault(Manifest.permission.ACCESS_BACKGROUND_LOCATION, false) -> {
                // Lastly, requests to disable battery optimisation
                requestPermissions(3)
            }

            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
                Toast.makeText(applicationContext, R.string.timeline_wont_work, Toast.LENGTH_LONG).show()
            }

            permissions.getOrDefault(Manifest.permission.POST_NOTIFICATIONS, false) -> {
                // Permission for notifications have been granted (Only needed on Android 13 and above)
                // No cation for now
            }

            else -> {
                // No action currently
//                Toast.makeText(applicationContext, R.string.timeline_wont_work, Toast.LENGTH_LONG).show()
            }
        }
    }

    // When notified that the map is ready, this method displays the current days records
    override fun onMapReady() {
        Log.i(TAG, "onMapReady received")
        getDaysRecords()
    }

    // Object which contains the result of the calendar picker
    private val dateSetListener = DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->

        // Sets the class variable "date" to the received date
        // ("month + 1", as the months need to be numbered 1-12 instead of 0-11)
        date = LocalDate.of(year, month+1, dayOfMonth)

        Log.i(TAG, "dateSetListener received date: " + date.toString())

        // Call the getDaysRecord function, to display all the records for the specific day
        getDaysRecords()
    }

    // Function which takes the date provided by dateSetListener and adjusts the map with markers
    private fun getDaysRecords() {

        Log.i(TAG, "getDaysRecords() called")

        // Takes the dateTime variable and changes it to the epoch time in milli at the start of said day
        // Zone offset is set to UTC time for now, a more advanced solution should be implemented in future
        val dayStart = date.atStartOfDay().toEpochSecond(ZoneOffset.of("+00:00")) * 1000

        // Takes the dayStart variable and adds 86399999 milliseconds which takes it to the millisecond before next midnight
        val dayEnd = dayStart + 86399999

        val locations: List<Location> = locationDao.retrieveForDate(dayStart, dayEnd)

        // Remove all markers already on the screen (if any)
        mMap.removeMarkers()

        if (locations.isNotEmpty()) {

            // Iterates through the received list and adds a marker to each location
            for (Location in locations) {
                val recordId = Location.recordId
                val date = Date(Location.recordTime)
                val time = Time(Location.recordTime).toString()
                val latitude = Location.latitude
                val longitude = Location.longitude

                val centre = LatLng(latitude!!, longitude!!)

                mMap.addMarker(centre, time)
            }

            // Centre the map on the last/latest location of the day
            mMap.centreMapAt(LatLng(locations.last().latitude!!, locations.last().longitude!!))

            Toast.makeText(applicationContext, R.string.displaying_results, Toast.LENGTH_SHORT).show()

        } else {
            Toast.makeText(applicationContext, R.string.records_not_found, Toast.LENGTH_SHORT).show()
        }
    }
}
