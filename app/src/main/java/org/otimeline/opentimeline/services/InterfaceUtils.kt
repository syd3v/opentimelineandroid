package org.otimeline.opentimeline.services

// Used to allow inter program communication

interface InterfaceUtils {

    // Allows the map fragment to tell the parent class that the map has loaded
    fun onMapReady()

}