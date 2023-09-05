package org.otimeline.opentimeline.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * LocationWorker can be scheduled by other activities or classes, it starts an instance of
 * foregroundLocationService when called
 *
 * The reason a worker is used, is to be able to use the PRIORITY_HIGH_ACCURACY setting when
 * retrieving location data from the phone. When a location update is received the
 * foregroundLocationService stops itself and reschedules a new worker for in x minutes time
 * where x is the interval set by the user. While the foregroundLocationService can set its
 * own schedule, using PRIORITY_HIGH_ACCURACY seems to constantly poll GPS, even when it is
 * a few minutes until the next location update being due.
 */

class LocationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    // Shared pref and class variables
    private val IS_TRACKING = "tracking"
    private val SETTINGS = "settings"
    private val START_FOREGROUND = "START"

    private val TAG = "LocationWorker"

    override fun doWork(): Result {

        Log.i(TAG, "doWork() called")

        val context = applicationContext

        // Initiate the shared preference interface
        val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

        // Does a permission check
        if (permissionChecker(context, prefs)) {

            // Checks that IS_TRACKING is set to true and then starts the foreground service
            if (prefs.getBoolean(IS_TRACKING, false)) {
                Log.i(TAG, "Starting foreground service")

                val serviceIntent = Intent(context, ForegroundLocationService::class.java)
                serviceIntent.setAction(START_FOREGROUND)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }

        return Result.success()
    }

    // Checks if the required permissions are still granted, if not it presents returns false
    private fun permissionChecker(context: Context, prefs: SharedPreferences): Boolean {

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
            prefs.edit().putBoolean(IS_TRACKING, false).apply()

            return false
        }
    }
}

