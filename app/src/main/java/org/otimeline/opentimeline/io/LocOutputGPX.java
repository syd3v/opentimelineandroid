package org.otimeline.opentimeline.io;


import android.content.Context;
import android.net.Uri;

import org.otimeline.opentimeline.database.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * LocOutputGPX takes a list of Location objects, and exports them to a specified URI
 *
 * While GPX files can be more advanced, this processor class simply outputs all the
 * data points as "wpt" objects, with no additional formatting.
 */


public class LocOutputGPX {

    // Lists of tags which can be used to keep track of all the tags
    // so they can be written at the end of a file or object
    static List<String> outerTagLevel = new ArrayList<String>();
    static List<String> innerTagLevel = new ArrayList<String>();


    // Class variables
    static String name = "name";
    static String wpt = "wpt";
    static String time = "time";


    /**
     * Formats a start tag for XML
     * @param tag The value to be formatted
     * @return The formatted start tag
     */
    public static String startTag (String tag) {
        return String.format("<%s>", tag);
    }

    /**
     * Formats a end tag for XML
     * @param tag The value to be formatted
     * @return The formatted end tag
     */
    public static String endTag (String tag) {
        return String.format("</%s>", tag);
    }

    /**
     * Takes a latitude and longitude value and formats it into a GPX "wpt" value
     * @param lat The latitude of the Location object
     * @param lon The longitude of the Location object
     * @return A formatted GPX "wpt" value
     */
    public static String locTag (double lat, double lon) {
        return String.format("<%s lat=\"%f\" lon=\"%f\">", wpt, lat, lon);
    }

    /**
     * Takes an epoch value, and converts it to a GPX compatible date/time format
     * @param epoch The epoch time to be converted
     * @return A valid GPX date/time value
     */
    public static String timeTag(Long epoch) {
        Instant time = Instant.ofEpochMilli(epoch);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(time, ZoneId.of("UTC"));

        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

        return localDateTime.format(format);
    }

    /**
     * Static method which takes a list of Locations, and outputs them to the specified GPX file location
     * @param locations List of Location objects
     * @param uri The URI where the file will be output to
     * @param context Android context passed in by the calling activity/service
     * @throws IOException
     */
    public static void writeLocationsToGPX(List<Location> locations, Uri uri, Context context) throws IOException {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            BufferedWriter locFile = new BufferedWriter(new OutputStreamWriter(outputStream));

            // Write XML header to the file
            locFile.write("<?xml version=\"1.0\"?>\n");

            // Write the gpx tag to the file
            locFile.write(startTag("gpx"));
            locFile.write("\n");

            // Give the files title tags and value
            locFile.write(startTag(name) + "Exported from Open Timeline" + endTag(name));
            locFile.write("\n");

            // Iterate through the Location list outputting a formatted "wpt" value
            for (Location location : locations) {
                System.out.println("Writing location at time " + location.getRecordTime() + " with lat " + location.getLatitude() + " and lon " + location.getLongitude());
                innerTagLevel.add(wpt);
                locFile.write(locTag(location.getLatitude(), location.getLongitude()));
                innerTagLevel.add(time);
                locFile.write(startTag(time) + timeTag(location.getRecordTime()));
                for (int i = innerTagLevel.size(); i > 0; i--) {
                    locFile.write(endTag(innerTagLevel.get(i - 1)));
                }
                locFile.write("\n");
                innerTagLevel.clear();
            }

            // Write the closing gpx tag and close the writer
            locFile.write(endTag("gpx"));
            locFile.close();
        }
    }

}
