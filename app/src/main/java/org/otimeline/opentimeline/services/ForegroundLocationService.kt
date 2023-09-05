/**
 * This class is a foreground service which retrieves the current user location and
 * sends it to the UI as a toast.
 * It is heavily based off a codelabs guide:
 * https://codelabs.developers.google.com/codelabs/while-in-use-location/
 *
 * The aim is to develop it further into a more mature and customised solution
 * This currently meets the first implementation target for this module
 */

package org.otimeline.opentimeline.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.otimeline.opentimeline.database.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import org.otimeline.opentimeline.MainActivity
import java.util.concurrent.TimeUnit
import org.otimeline.opentimeline.R

/**
 * ForegroundLocationService implements a foreground location service
 *
 * While some of its functions such as binding have been developed, as they were
 * needed during testing (so can be used), when started, the service creates an
 * instance of a foreground service, which runs until it receives a location update.
 * When it retrieves a location update, it schedules a new LocationWorker instance
 * based on the users chosen interval preference, which will restart the service
 * when the system activates it.
 *
 * While fusedLocationProvider allows for an interval to be requested, with updates
 * received around the requested time, during testing if PRIORITY_HIGH_ACCURACY was
 * used for the GPS priority, GPS would constantly be polled, even if it was a few
 * minutes until the next update. The implemented method means, the foreground service,
 * its notification, and GPS polling only runs when needed.
 */


// The class implements the Service class
class ForegroundLocationService : Service() {

    val TAG = "ForegroundLocationService"

    // Create a val with an instance of the LSBinder() class for future communication
    private val binder = LSBinder()

    // Create an instance of the fused location provider
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    // Location request object to pass to construct with variables such as intervals
    private lateinit var locationRequest: LocationRequest

    // Receiver object for location data when a callback is received
    private lateinit var locationCallback: LocationCallback

    // Used to store the latest location in a a Location variable for future use
    private var currentLocation: Location? = null

    // Create database and database DAO variables
    private lateinit var db: LocationsDB
    private lateinit var locationDao: LocationsDBDao

    // Variables to be used by the getters for the latest location
    var latitude: Double = 0.000
    var longitude: Double = 0.000

    // Variables to store the previous lat/lon, (not currently needed, as a new instance
    // of the foreground service is run each time
    var prevLatitude: Double = 0.000
    var prevLongitude: Double = 0.000

    // Shared pref and class variables
    private val START_FOREGROUND = "START"
    private val STOP_FOREGROUND = "STOP"
    private val INTERVAL = "interval"
    private val SETTINGS = "settings"
    private lateinit var notificationTitle: String

    override fun onCreate() {
        Log.i(TAG, "onCreate() called")

        // Set the notification title
        notificationTitle = resources.getString(R.string.notification_title)

        // Gets an instance of fused location provider
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Gets an instance of shared preferences
        val prefs = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

        // Build the location request for the fused provider
        locationRequest = LocationRequest.create().apply {

            // Retrieve the interval set by the user
            val intervalPref = 2L

            // Set the interval for location updates, may not be exact
            interval = TimeUnit.MINUTES.toMillis(intervalPref)

            // Set the the fastest interval possible
            fastestInterval = TimeUnit.MINUTES.toMillis(intervalPref - 1)

            // Set the max interval to wait between updates
            maxWaitTime = TimeUnit.MINUTES.toMillis(intervalPref + 1)

            // Set the request priority to PRIORITY_HIGH_ACCURACY, during testing the priority BALANCED
            // was unreliable.
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Create an instance of locationCallback, which takes the received package of data and processes it
        // It will add the location to the database if it is different to the previous record
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                Log.i(TAG, "Location receive activated")

                // Assign the class variables with the new location data
                currentLocation = locationResult.lastLocation
                latitude = locationResult.lastLocation.latitude
                longitude = locationResult.lastLocation.longitude

                // Only adds a record to the database if it is different to the previous record
                // Currently will always return false as a new instance of the foreground service is run each time
                if ((latitude == prevLatitude) && (longitude == prevLongitude)) {
                    Log.i(TAG,"Location received, but not recorded as same as last record")
                } else {
                    val location = Location(
                        latitude = latitude,
                        longitude = longitude
                    )

                    locationDao.insertLocation(location)
                }

                // Assign the location to the previous record variables
                prevLatitude = latitude
                prevLongitude = longitude

                // Retrieve the the users preferred interval time
                val interval = prefs.getLong(INTERVAL, 2)

                // Schedule the work manager to run based on the users chosen interval time
                val nextLocationScheduler = OneTimeWorkRequestBuilder<LocationWorker>()
                    .setInitialDelay(interval, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(applicationContext).enqueue(nextLocationScheduler)
                Log.i(TAG, "Scheduled next request for in $interval minutes time")

                // Unsubscribe and then stop the foreground service
                Log.i(TAG, "Attempting to stop foreground service")
                unsubscribeToLocationUpdates()
                stopSelf()
            }
        }

        // Create the database and database DAO
        // This builder also forces the main thread queries, which is not recommended
        db = Room.databaseBuilder(
            applicationContext,
            LocationsDB::class.java, "locationsDb"
        ).allowMainThreadQueries().build()
        locationDao = db.locationsDao()

        // Subscribe to location updates
        subscribeToLocationUpdates()

        // If the correct permissions are not detected, the foreground service will destroy itself
        if (!permissionChecker(applicationContext)) {
            unsubscribeToLocationUpdates()
            stopSelf()
        }
    }

    // Function to subscribe to location updates
    fun subscribeToLocationUpdates() {
        Log.i(TAG, "subscribeToLocationUpdates() called")

        // Officially start the service
        startService(Intent(applicationContext, ForegroundLocationService::class.java))

        /** Attempt to get the location
         * @locationRequest is the call/object to ask for the current location
         * @locationCallback is the locationCallback method which processes the data package
         * The looper creates a loop for the fusedlocation service to send the callback on
         */
        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // In the original tutorial, shared preferences were updated to show permission had not been given
            Log.i(TAG, "Probably don't have location permissions")
            e.printStackTrace()
        }

    }

    // Function to unsubscribe from location updates
    fun unsubscribeToLocationUpdates() {
        Log.i(TAG, "unsubscribeToLocationUpdates() called")

        try {
            val removeTask = fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            removeTask.addOnCompleteListener {task ->
                if (task.isSuccessful) {
                    Log.i(TAG, "Location un-subscription successful")
                    stopSelf()
                } else {
                    Log.i(TAG, "Failed to unsubscribe")
                }

            }

        } catch (e: SecurityException) {
            // Sharedpref missed out
            Log.i(TAG, "Probably don't have location permissions")
            e.printStackTrace()
        }
    }

    // Set up so it can take either a start or stop command, I have had to first start the service
    // in the stop code, as otherwise Android will crash the app, as it expects startForeground to
    // be called
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand() Called")

        // Detects whether the request to start or stop the foreground service has been called
        // and takes appropriate action
        if(intent?.action.equals(START_FOREGROUND)) {
            Log.i(TAG, "Attempting to start foreground service")

            // Initiate the foreground service
            startForeground(12345, notification())
        } else if (intent?.action.equals(STOP_FOREGROUND)) {
            Log.i(TAG, "Attempting to stop foreground service")

            // Added a start command, as otherwise the app crashes as startForeground must be called
            startForeground(12345, notification())

            // Unsubscribe and then stop the foreground service
            unsubscribeToLocationUpdates()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    /**
     * Allows activities to bind and unbind from the location service
     */
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind() Called")

        return binder

    }

    override fun onUnbind(intent: Intent?): Boolean {

        Log.i(TAG, "onUnbind() called")

        startForeground(12345, notification())

        return super.onUnbind(intent)
    }

    /**
     * Implementation of the Binder object
     */
    inner class LSBinder : Binder() {
        internal val service: ForegroundLocationService
            // Returning an instance of this service should allow clients to call public methods
            get() = this@ForegroundLocationService
    }


    // Getters for the latitude and longitude which can be used by subscribed activities
    fun getLat(): Double {
        return latitude
    }

    fun getLon(): Double {
        return longitude
    }

    // This code is only supposed to be run on API 26 or above, I have not "guarded" it
    // as the minSdk for this project is 30.
    // This function creates the notification channel to be used by the foregrounds notification
    private fun notificationChannel() {
        // Create the channel properties
        val mChannel = NotificationChannel("serviceChannel", "Location service", NotificationManager.IMPORTANCE_DEFAULT)
        mChannel.description = "These notifications are used by the location service to keep running in the background"
        mChannel.importance = NotificationManager.IMPORTANCE_LOW // Set the notification priority to low/silent

        // Register the channel with the OS
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }

    // This function is called to create the notification, it also calls the notificationChannel
    // function to register the channel in case it has not been registered previously
    private fun notification(): Notification {

        Log.i(TAG, "Notification builder called")

        notificationChannel()

        // Creating the intent for the action of the notification if it is clicked
        // In this case it is set to open the main activity
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)

        // Build the notification
        val builder = Notification.Builder(this, "serviceChannel")
            .setSmallIcon(R.drawable.satellite_icon)
            .setShowWhen(false) // Disables notification time
            .setOngoing(true) // Make sure the user cannot dismiss it
            .setContentIntent(activityPendingIntent)
            .setContentTitle(notificationTitle).build()

        return builder
    }

    // Checks if the required permissions are still granted
    private fun permissionChecker(context: Context): Boolean {

        if (((ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED))
        ) {
            Log.i(TAG, "Permission check is true")
            return true
        } else {
            Log.i(TAG, "Permission check is false")
            return false
        }
    }

}