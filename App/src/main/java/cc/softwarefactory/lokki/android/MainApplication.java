/*
Copyright (c) 2014-2015 F-Secure
See LICENSE for details
*/
package cc.softwarefactory.lokki.android;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import cc.softwarefactory.lokki.android.utilities.AnalyticsUtils;
import cc.softwarefactory.lokki.android.utilities.PreferenceUtils;

public class MainApplication extends Application {

    /**
     * Indicates whether this is a development or production build
     */
    private static final boolean DEVELOPER_MODE = true;

    /**
     * Debug tag identifying that a log message was caused by the main application
     */
    private static final String TAG = "MainApplication";

    /**
     * Int array enumerating the codes used for different Google Maps map view types
     */
    public static final int[] mapTypes = {GoogleMap.MAP_TYPE_NORMAL, GoogleMap.MAP_TYPE_SATELLITE, GoogleMap.MAP_TYPE_HYBRID};
    /**
     * Currently selected Google Maps map view type.
     * TODO: make private with proper accessor methods to disallow values not in mapTypes
     */
    public static int mapType = 0;
    public static String emailBeingTracked;
    /**
     * User dashboard JSON object. Format:
     * {
     *      "battery":"",
     *      "canseeme":["c429003ba3a3c508cba1460607ab7f8cd4a0d450"],
     *      "icansee":{
     *          "c429003ba3a3c508cba1460607ab7f8cd4a0d450":{
     *              "battery":"",
     *              "location":{},
     *              "visibility":true
     *          }
     *      },
     *      "idmapping":{
     *          "a1b2c3d4e5f6g7h8i9j10k11l12m13n14o15p16q":"test@test.com",
     *          "c429003ba3a3c508cba1460607ab7f8cd4a0d450":"family.member@example.com"
     *      },
     *      "location":{},
     *      "visibility":true
     * }
     */
    public static JSONObject dashboard = null;
    public static String userAccount; // Email
    public static JSONObject contacts;
    /**
     * Format:
     * {
     *      "Test Friend":"test.friend@example.com",
     *      "Family Member":"family.member@example.com",
     *      "Work Buddy":"work.buddy@example.com"
     * }
     */
    public static JSONObject mapping;
    /**
     * Contacts that aren't shown on the map. Format:
     * {
     *      "test.friend@example.com":1,
     *      "family.member@example.com":1
     * }
     */
    public static JSONObject iDontWantToSee;
    public static Boolean visible = true;
    public static LruCache<String, Bitmap> avatarCache;
    public static JSONObject places;
    public static boolean locationDisabledPromptShown;
    public static JSONArray buzzPlaces;


    @Override
    public void onCreate() {

        Log.d(TAG, "Lokki started component");

        //Load user settings
        loadSetting();

        AnalyticsUtils.initAnalytics(getApplicationContext());

        locationDisabledPromptShown = false;

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024); // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;
        avatarCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than number of items.
                return (bitmap.getRowBytes() * bitmap.getHeight()) / 1024;
            }
        };


        // get mapValue from preferences
        mapValue = PreferenceUtils.getString(getApplicationContext(), PreferenceUtils.KEY_SETTING_MAP_MODE);

        if(!mapValue.isEmpty()){
            try{
                mapType = Integer.parseInt(mapValue); // set mapValue to mapType
            }catch (Error e){
                mapType = 0;
            }
        }else {
            mapType = 0; // if empty set to default
        }


        String iDontWantToSeeString = PreferenceUtils.getString(this, PreferenceUtils.KEY_I_DONT_WANT_TO_SEE);
        if (!iDontWantToSeeString.isEmpty()) {
            try {
                MainApplication.iDontWantToSee = new JSONObject(iDontWantToSeeString);
            } catch (JSONException e) {
                MainApplication.iDontWantToSee = null;
                Log.e(TAG, e.getMessage());
            }
        } else {
            MainApplication.iDontWantToSee = new JSONObject();
        }
        Log.d(TAG, "MainApplication.iDontWantToSee: " + MainApplication.iDontWantToSee);

        if (DEVELOPER_MODE) {

            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        buzzPlaces = new JSONArray();

        super.onCreate();
    }

    private void loadSetting() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        visible = PreferenceUtils.getBoolean(getApplicationContext(), PreferenceUtils.KEY_SETTING_VISIBILITY);
        Log.d(TAG, "Visible: " + visible);
    }

}
