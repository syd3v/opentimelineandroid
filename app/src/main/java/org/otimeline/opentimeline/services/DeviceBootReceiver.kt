package org.otimeline.opentimeline.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startForegroundService
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.otimeline.opentimeline.services.*
import com.google.android.material.snackbar.Snackbar

/**
 * This class receives the on boot receiver from the Android system, allowing the app
 * to carry on tracking if the device is restarted
 *
 * It checks if tracking is enabled, and starts an instance of LocationWorker, if
 * true
 */

class DeviceBootReceiver : BroadcastReceiver() {

    private val TAG = "DeviceBroadcastReceiver"

    // Shared pref and class variables
    private val IS_TRACKING = "tracking"
    private val SETTINGS = "settings"
    private val WORK_ID = "scheduledTask"


    override fun onReceive(context: Context?, intent: Intent?) {

        Log.i(TAG, "Received boot broadcast")

        // Initiate the shared preference interface
        val prefs = context?.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

        val startTracking = prefs?.getBoolean(IS_TRACKING, false)

        // Starts up the tracking service if the user has set indicated they want it running
        if (startTracking == true) {

            Log.i(TAG, "Tracking is true, attempting to start tracking")

            // Cancels all tasks with the task and starts a new chain
            // This prevents having multiple requests running at the same time
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_ID)

            val locationWorker = OneTimeWorkRequestBuilder<LocationWorker>()
                .addTag(WORK_ID)
                .build()
            WorkManager.getInstance(context).enqueue(locationWorker)
        } else {
            Log.i(TAG, "Tracking is false, not starting tracking")
        }

    }

}