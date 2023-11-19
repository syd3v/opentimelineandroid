package org.otimeline.opentimeline.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.otimeline.opentimeline.services.InterfaceUtils
import org.otimeline.opentimeline.R

/**
 * This fragment class implements the apps map. It also receives an instance of the InterfaceUtils
 * interface, so it can notify the parent class that it is ready to receive instructions.
 */

class MapFragment(private val onMapReadyInterface: InterfaceUtils) : Fragment(), OnMapReadyCallback {

    private val TAG = "MapFragment"

    // Variables used to interact with the map ui and list of markers on screen
    private lateinit var mView: MapView
    private lateinit var mMap: GoogleMap
    private var markers: MutableList<Marker> = mutableListOf()


    // Point the fragment at the correct UI container
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.map_fragment, container, false)

        mView = view.findViewById(R.id.map_container)
        mView.onCreate(savedInstanceState)
        mView.getMapAsync(this)

        return view
    }

    // Implement the onMapReady function to set the map up once it is ready
    override fun onMapReady(gMap: GoogleMap) {
        Log.i(TAG, "onMapReady received")

        // Initialising the local variable so it can be accessed by class functions
        mMap = gMap

        // Setting the map type and controls
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        //Notifying the parent class that the map is ready
        onMapReadyInterface.onMapReady()
    }

    // Takes a LatLng and centres the map on its coordinates
    fun centreMapAt(centre: LatLng) {
        mMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                centre,
                15F
            )
        )
    }

    // Takes a LatLng, adds a marker to the map, and then stores the generated object in the markers list
    fun addMarker(centre: LatLng, title: String) {
        Log.i(TAG, "addMarker function called")

        val marker: Marker? = mMap.addMarker(MarkerOptions().position(centre).title(title))
        markers.add(marker!!)

    }

    // Removes all markers in the list on the map, and then clears the list
    fun removeMarkers() {
        Log.i(TAG, "removeMarkers function called")

        markers.forEach { it.remove() }
        markers.clear()

    }

    // Overriding the required methods for the map view to function
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

    override fun onLowMemory() {
        super.onLowMemory()
        mView.onLowMemory()
    }
}