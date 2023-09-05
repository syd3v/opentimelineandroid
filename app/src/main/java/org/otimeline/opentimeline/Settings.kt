package org.otimeline.opentimeline

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.opengl.Visibility
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.SyncStateContract.Constants
import android.text.Editable
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.otimeline.opentimeline.services.ForegroundLocationService
import org.otimeline.opentimeline.services.LocationWorker
import java.util.concurrent.TimeUnit

/**
 * This activity provides the settings page for the user to configure the app
 *
 * It allows them to enable/disable tracking, set the tracking interval, as well
 * as read the apps privacy policy
 *
 * Additionally, if the user has asked the app to not show them a permission dialog,
 * another button allowing them to reverse the request is shown
 */


class Settings : AppCompatActivity() {

    // Variable to interact with toolbar and buttons
    private lateinit var actionBar: ActionBar

    // Set the buttons
    private lateinit var serviceButton: Button
    private lateinit var getInterval: Button
    private lateinit var resetUserPermPref: Button
    private lateinit var privacyPolicy: Button

    // Set the input views
    private lateinit var intervalInput: EditText

    // Shared preference and other constants
    private val IS_TRACKING = "tracking"
    private val SETTINGS = "settings"
    private val START_FOREGROUND = "START"
    private val STOP_FOREGROUND = "STOP"
    private val INTERVAL = "interval"
    private val NOPERMISSIONREQUEST = "noPermissionRequest"
    private val WORK_ID = "scheduledTask"

    private val TAG = "SettingsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "onCreate() called")

        val RECORDING = resources.getString(R.string.button_recording_set)
        val NOT_RECORDING = resources.getString(R.string.button_not_recording_set)

        // Set the content view
        setContentView(R.layout.settings_layout)

        val context = applicationContext

        // Create shared preferences object
        val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

        // Set the serviceButton to either recording or not recording
        serviceButton = findViewById(R.id.shared_pref_bool)
        if (prefs.contains(IS_TRACKING)) {
            if ((prefs.getBoolean(IS_TRACKING, false))) {
                serviceButton.text = RECORDING
            } else {
                serviceButton.text = NOT_RECORDING
            }
        } else {
            prefs.edit().putBoolean(IS_TRACKING, false).apply()
            serviceButton.text = NOT_RECORDING
        }

        // Depending on if the service is running or not, the button will either start or stop
        // the foreground location service
        serviceButton.setOnClickListener {

            if (prefs.getBoolean(IS_TRACKING, false)) {
                serviceButton.text = NOT_RECORDING
                prefs.edit().putBoolean(IS_TRACKING, false).apply()
                Log.i(TAG, "Tracking set to false")

                // Cancel any queued work
                WorkManager.getInstance(context).cancelAllWorkByTag(WORK_ID)

                // Directly instruct the location foreground activity to stop
                val serviceIntent = Intent(context, ForegroundLocationService::class.java)
                serviceIntent.setAction(STOP_FOREGROUND)
                ContextCompat.startForegroundService(context, serviceIntent)

                Toast.makeText(context, R.string.tracking_disabled, Toast.LENGTH_SHORT).show()

            } else {
                serviceButton.text = RECORDING
                prefs.edit().putBoolean(IS_TRACKING, true).apply()
                Log.i(TAG, "Tracking set to true")

                // Ask the LocationWorker to start the scheduled location retrieval chain
                val locationWorker = OneTimeWorkRequestBuilder<LocationWorker>()
                    .addTag(WORK_ID)
                    .build()
                WorkManager.getInstance(context).enqueue(locationWorker)

                Toast.makeText(context, R.string.tracking_enabled, Toast.LENGTH_SHORT).show()
            }
        }
        serviceButton.setOnLongClickListener {
            Toast.makeText(applicationContext, R.string.tracking_button_message, Toast.LENGTH_SHORT).show()
            true
        }

        // Set up the view and set interval of tracking request settings
        getInterval = findViewById(R.id.set_interval)
        intervalInput = findViewById(R.id.interval_input)

        intervalInput.setText(prefs.getLong(INTERVAL, 2).toString())

        // Takes input from the user and sets the shared preference interval to the new interval
        getInterval.setOnClickListener {
            // Retrieve the input interval by the user and convert it to a long
            val minuteInterval = intervalInput.text.toString().toLong()

            if (minuteInterval in 1..1440) {

                prefs.edit().putLong(INTERVAL, minuteInterval).apply()

                // Building the string with the required variable
                val toastText =
                    getString(R.string.interval_set_message, minuteInterval)

                Toast.makeText(applicationContext, toastText, Toast.LENGTH_SHORT).show()
            } else if (minuteInterval > 1440) {
                val toastText =
                    getString(R.string.number_too_high, minuteInterval)
                Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show()
            } else {
                val toastText =
                    getString(R.string.number_too_low, minuteInterval)
                Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show()
            }
        }
        getInterval.setOnLongClickListener {
            Toast.makeText(applicationContext, R.string.set_interval_button_message, Toast.LENGTH_LONG).show()
            true
        }

        // Displays the reset permission request setting
        resetUserPermPref = findViewById(R.id.reset_permission_user_pref)
        if (prefs.getBoolean(NOPERMISSIONREQUEST, false)) {
            resetUserPermPref.setOnClickListener {
                prefs.edit().putBoolean(NOPERMISSIONREQUEST, false).apply()
                resetUserPermPref.visibility = View.INVISIBLE
                Toast.makeText(this, R.string.permission_request_activated, Toast.LENGTH_SHORT).show()
            }
        } else {
            resetUserPermPref.visibility = View.INVISIBLE
        }
        resetUserPermPref.setOnLongClickListener {
            Toast.makeText(applicationContext, R.string.get_permission_request_button_message, Toast.LENGTH_SHORT).show()
            true
        }

        // Small pop up to inform the user of data being collected when button pressed
        privacyPolicy = findViewById(R.id.privacy_policy)
        privacyPolicy.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.privacy_policy_title)
                .setMessage(R.string.privacy_policy_text)
                .setPositiveButton(R.string.ok) { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.dismiss()
                }
                .show()
        }
        privacyPolicy.setOnLongClickListener {
            Toast.makeText(applicationContext, R.string.privacy_policy_button_message, Toast.LENGTH_SHORT).show()
            true
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

}