package eu.faircode.finegeotag;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class LocationService extends IntentService {
    private static final String TAG = "FineGeotag.Service";

    public static final String ACTION_LOCATION = "Location";
    public static final String ACTION_ALARM = "Alarm";

    public LocationService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.w(TAG, "Intent=" + intent);
        String image_filename = intent.getData().getPath();

        if (ACTION_LOCATION.equals(intent.getAction())) {
            Location location = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
            Log.w(TAG, "Location=" + location + " image=" + image_filename);
            if (location != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

                // Check accuracy
                if (location.getAccuracy() > Float.parseFloat(prefs.getString(ActivitySettings.PREF_ACCURACY, "50"))) {
                    Log.w(TAG, "Inaccurate image=" + image_filename);
                    return;
                }

                // Cancel further updates
                cancelUpdates(image_filename);

                try {
                    // Write Exif
                    ExifInterfaceEx exif = new ExifInterfaceEx(image_filename);
                    exif.setLocation(location);
                    exif.saveAttributes();
                    Log.w(TAG, "Exif updated image=" + image_filename);

                    // Geocode
                    if (prefs.getBoolean(ActivitySettings.PREF_TOAST, true)) {
                        String address = geocode(location);
                        if (address == null)
                            address = getString(R.string.msg_geotagged);
                        notify(image_filename, address);
                    }
                } catch (IOException ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }
            }

        } else if (ACTION_ALARM.equals(intent.getAction())) {
            cancelUpdates(image_filename);
            Log.w(TAG, "Timeout image=" + image_filename);
        }
    }

    private void cancelUpdates(String image_filename) {
        // Cancel location updates
        Intent locationIntent = new Intent(this, LocationService.class);
        locationIntent.setAction(LocationService.ACTION_LOCATION);
        locationIntent.setData(Uri.fromFile(new File(image_filename)));
        PendingIntent pi = PendingIntent.getService(this, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        lm.removeUpdates(pi);

        // Cancel alarm
        Intent alarmIntent = new Intent(this, LocationService.class);
        alarmIntent.setAction(LocationService.ACTION_ALARM);
        alarmIntent.setData(Uri.fromFile(new File(image_filename)));
        PendingIntent pia = PendingIntent.getService(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.cancel(pia);
    }

    private String geocode(Location location) throws IOException {
        String address = null;
        if (Geocoder.isPresent()) {
            Geocoder geocoder = new Geocoder(this);
            List<Address> listPlace = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (listPlace != null && listPlace.size() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int l = 0; l < listPlace.get(0).getMaxAddressLineIndex(); l++) {
                    if (l != 0)
                        sb.append("\n");
                    sb.append(listPlace.get(0).getAddressLine(l));
                }
                address = sb.toString();
            }
        }
        return address;
    }

    private void notify(final String image_filename, final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                LayoutInflater inflater = LayoutInflater.from(LocationService.this);
                View layout = inflater.inflate(R.layout.geotagged, null);

                ImageView iv = (ImageView) layout.findViewById(R.id.image);
                iv.setImageURI(Uri.fromFile(new File(image_filename)));
                TextView tv = (TextView) layout.findViewById(R.id.text);
                tv.setText(text);

                Toast toast = new Toast(getApplicationContext());
                toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setView(layout);
                toast.show();
            }
        });
    }
}
