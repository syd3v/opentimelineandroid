package org.otimeline.opentimeline.io;

import android.content.Context;
import android.net.Uri;

import org.otimeline.opentimeline.database.*;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * SAXInputGPX contains a static method which can retrieve "wpt" records
 * from a GPX formatted data file using a SAX parser. It turns the data
 * into Location objects which are then returned and can be processed
 * by the calling class.
 */

public class SAXInputGPX {

    private static double lat;
    private static double lon;
    private static boolean bTime = false;

    private static List<Location> locations = new ArrayList<>();

    /**
     * This static method allows a calling class to retrieve "wpt" values from a GPX formatted file
     * @param documentUri The URI of the file to be processed
     * @param context Android context passed in by the calling activity/service
     * @return A list of Locations retrieved from the specified file
     */
    public static List<Location> readLocationsFromGPX(Uri documentUri, Context context) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(documentUri)) {
            InputSource inputUri = new InputSource(inputStream);

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();


            DefaultHandler handler = new DefaultHandler() {
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

                    // Finds XML instances of "wpt", and retrieves the data. Passes the date/time to the characters method
                    if (qName.equalsIgnoreCase("wpt")) {

                        // Stores retrieved values in the class variables
                        lat = Double.parseDouble(attributes.getValue("lat"));
                        lon = Double.parseDouble(attributes.getValue("lon"));
                    }

                    if (qName.equalsIgnoreCase("time")) {
                        bTime = true;
                    }
                }

                public void characters(char[] ch, int start, int length) throws SAXException {

                    if (bTime) {
                        // Takes the received date and time and converts it back into a epoch value
                        // Inside a try block, as otherwise importing would fail on one corrupt record
                        try {
                            // Formats the date/time into a LocalDateTime object
                            String str = new String(ch, start, length);
                            DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));
                            LocalDateTime date = LocalDateTime.parse(str, df);

                            // Converts the date object to an epoch time value
                            long epoch = date.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
                            System.out.println("Retrieved location at time " + epoch + " with lat " + lat + " and lon " + lon);

                            // Adding the data to the class Location list
                            locations.add(new Location(1, epoch, lat, lon));
                        } catch (Exception e) {
                            String str = new String(ch, start, length);
                            System.out.println("Failed to import record: " + str);
                            e.printStackTrace();
                        }

                        bTime = false;
                    }
                }
            };

            saxParser.parse(inputUri, handler);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return locations;
    }
}
