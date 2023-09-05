package org.otimeline.opentimeline

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.room.Room
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.time.LocalDate
import java.util.Calendar
import org.otimeline.opentimeline.database.*
import org.otimeline.opentimeline.services.*
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

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    // Logging tag
    private val TAG = "MainActivity"

    // Variables used to interact with the map ui and list of markers on screen
    private lateinit var mView: MapView
    private lateinit var mMap: GoogleMap
    private var markers: MutableList<Marker> = mutableListOf()

    // Variable to interact with toolbar and buttons
    private lateinit var actionBar: ActionBar


    // Create database and database DAO variables
    private lateinit var db: LocationsDB
    private lateinit var locationDao: LocationsDBDao

    // Button and object used to present the user with a calendar dialog
    private lateinit var date: LocalDate
    private var cal = Calendar.getInstance()

    // Variables for shared preferences
    private val IS_TRACKING = "tracking"
    private val SETTINGS = "settings"
    private val NOPERMISSIONREQUEST = "noPermissionRequest"
    private val WORK_ID = "scheduledTask"

    override fun onCreate(savedInstanceState: Bundle?) {

        Log.d(TAG, "onCreate called")

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

        // Set up the map
        mView = findViewById(R.id.map_frame)
        mView.onCreate(savedInstanceState)
        mView.getMapAsync(this)

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


    // Overrides onMapReady so
    override fun onMapReady(gMap: GoogleMap) {
        Log.i(TAG, "onMapReady received")

        // Initialising the local variable so the map object can be accessed by other methods
        mMap = gMap

        // Setting the map type and controls
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        // The following retrieves the previous location and focuses the map on it
        val lastLocation = locationDao.retrieveLatestLocation()
        if (lastLocation != null) {
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        lastLocation.latitude!!,
                        lastLocation.longitude!!
                    ), 15F
                )
            )
        } else {
            // If no records are found the map is focused on London
            val london = LatLng(51.5, -0.13)
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    london,
                    15F
                )
            )
        }
    }

    // Takes a LatLng, adds a marker to the map, and then stores the generated object in the markers list
    private fun addMarker(centre: LatLng, title: String) {

        val marker: Marker? = mMap.addMarker(MarkerOptions().position(centre).title(title))
        markers.add(marker!!)

    }

    // Removes all markers in the list on the map, and then clears the list
    private fun removeMarkers() {

        markers.forEach { it.remove() }
        markers.clear()

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
        // Zone offset is set to BST time for now, a more advanced solution should be implemented in future
        val dayStart = date.atStartOfDay().toEpochSecond(ZoneOffset.of("+01:00")) * 1000

        // Takes the dayStart variable and adds 86399999 milliseconds which takes it to the millisecond before next midnight
        val dayEnd = dayStart + 86399999

        val locations: List<Location> = locationDao.retrieveForDate(dayStart, dayEnd)

        // Remove all markers already on the screen (if any)
        removeMarkers()

        if (locations.isNotEmpty()) {

            // Iterates through the received list and adds a marker to each location
            for (Location in locations) {
                val recordId = Location.recordId
                val date = Date(Location.recordTime)
                val time = Time(Location.recordTime).toString()
                val latitude = Location.latitude
                val longitude = Location.longitude

                val centre = LatLng(latitude!!, longitude!!)

                addMarker(centre, time)
            }

            Toast.makeText(applicationContext, R.string.displaying_results, Toast.LENGTH_SHORT).show()

        } else {
            Toast.makeText(applicationContext, R.string.records_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    // As MapView is being used, all the following methods must be implemented/overridden, more details can be found at:
    // https://developers.google.com/maps/documentation/android-sdk/reference/com/google/android/libraries/maps/MapView
    override fun onStart() {
        super.onStart()
        mView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        mView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mView.onLowMemory()
    }
}
