/*
Copyright (c) 2014-2015 F-Secure
See LICENSE for details
*/
package cc.softwarefactory.lokki.android.activities;

import android.support.v7.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.androidquery.AQuery;

import org.json.JSONException;

import cc.softwarefactory.lokki.android.MainApplication;
import cc.softwarefactory.lokki.android.R;
import cc.softwarefactory.lokki.android.datasources.contacts.ContactDataSource;
import cc.softwarefactory.lokki.android.datasources.contacts.DefaultContactDataSource;
import cc.softwarefactory.lokki.android.fragments.AboutFragment;
import cc.softwarefactory.lokki.android.fragments.AddContactsFragment;
import cc.softwarefactory.lokki.android.fragments.ContactsFragment;
import cc.softwarefactory.lokki.android.fragments.MapViewFragment;
import cc.softwarefactory.lokki.android.fragments.NavigationDrawerFragment;
import cc.softwarefactory.lokki.android.fragments.PlacesFragment;
import cc.softwarefactory.lokki.android.fragments.PreferencesFragment;
import cc.softwarefactory.lokki.android.services.DataService;
import cc.softwarefactory.lokki.android.services.LocationService;
import cc.softwarefactory.lokki.android.utilities.AnalyticsUtils;
import cc.softwarefactory.lokki.android.utilities.PreferenceUtils;
import cc.softwarefactory.lokki.android.utilities.ServerApi;
import cc.softwarefactory.lokki.android.utilities.Utils;
import cc.softwarefactory.lokki.android.utilities.gcm.GcmHelper;



public class MainActivity extends AppCompatActivity implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CODE_EMAIL = 1001;
    private static final int REQUEST_TERMS = 1002;

    public static final String TAG_MAP_FRAGMENT = "mapFragment";
    public static final String TAG_PLACES_FRAGMENT = "placesFragment";
    public static final String TAG_CONTACTS_FRAGMENT = "contactsFragment";
    public static final String TAG_ADD_CONTACTS_FRAGMENT = "addContactsFragment";
    public static final String TAG_PREFERENCES_FRAGMENT = "preferencesFragment";
    public static final String TAG_ABOUT_FRAGMENT = "aboutFragment";

    private NavigationDrawerFragment mNavigationDrawerFragment;
    private CharSequence mTitle;
    private int selectedOption = 0;

    private ContactDataSource mContactDataSource;

    // TODO: make non static, put in shared prefs
    public static Boolean firstTimeLaunch;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(TAG, "onCreate");
        mContactDataSource = new DefaultContactDataSource();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTitle = getTitle();

        mNavigationDrawerFragment = (NavigationDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout)); // Set up the drawer.

        AQuery aq = new AQuery(findViewById(R.id.drawer_layout));
        aq.id(R.id.user_popout_menu_button).clicked(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Clicked user menu button");
                showUserPopupMenu(v);
            }
        });

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_layout);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mTitle);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public void showUserPopupMenu(View v){
        PopupMenu menu = new PopupMenu(this, v);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
            @Override
            public boolean onMenuItemClick(MenuItem item){
                switch (item.getItemId()){
                    case R.id.signout :
                        mNavigationDrawerFragment.toggleDrawer();
                        logout();
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.inflate(R.menu.user_menu);
        menu.show();

    }


    @Override
    protected void onStart() {

        super.onStart();
        Log.d(TAG, "onStart");

        if (firstTimeLaunch == null) {
            firstTimeLaunch = firstTimeLaunch();
        }

        if (firstTimeLaunch) {
            Log.i(TAG, "onStart - firstTimeLaunch, so showing terms.");
            startActivityForResult(new Intent(this, FirstTimeActivity.class), REQUEST_TERMS);
        } else {
            checkIfUserIsLoggedIn(); // Log user In
        }

    }

    private boolean firstTimeLaunch() {

        return PreferenceUtils.getString(this, PreferenceUtils.KEY_AUTH_TOKEN).isEmpty();
    }

    @Override
    protected void onResume() {

        super.onResume();
        Log.d(TAG, "onResume");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // WAKE_LOCK

        if (firstTimeLaunch || firstTimeLaunch()) {
            Log.i(TAG, "onResume - firstTimeLaunch, so avoiding launching services.");
            return;
        }


        Log.i(TAG, "onResume - NOT firstTimeLaunch, so launching services.");
        startServices();
        LocalBroadcastManager.getInstance(this).registerReceiver(exitMessageReceiver, new IntentFilter("EXIT"));
        LocalBroadcastManager.getInstance(this).registerReceiver(switchToMapReceiver, new IntentFilter("GO-TO-MAP"));


        Log.i(TAG, "onResume - check if dashboard is null");
        if (MainApplication.dashboard == null) {
            Log.w(TAG, "onResume - dashboard was null, get dashboard from server");
            ServerApi.getDashboard(getApplicationContext());
        }
    }


    private void startServices() {

        if (MainApplication.visible) {
            LocationService.start(this.getApplicationContext());
        }

        DataService.start(this.getApplicationContext());

        try {
            ServerApi.requestUpdates(this.getApplicationContext());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        // Fixes buggy avatars after leaving the app from the "Map" screen
        MainApplication.avatarCache.evictAll();
        LocationService.stop(this.getApplicationContext());
        DataService.stop(this.getApplicationContext());
        LocalBroadcastManager.getInstance(this).unregisterReceiver(switchToMapReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(exitMessageReceiver);
        super.onPause();
    }

    private void checkIfUserIsLoggedIn() {

        String userAccount = PreferenceUtils.getString(this, PreferenceUtils.KEY_USER_ACCOUNT);
        String userPassword = PreferenceUtils.getString(this, PreferenceUtils.KEY_USER_PASSWORD);
        String userId = PreferenceUtils.getString(this, PreferenceUtils.KEY_USER_ID);
        String authorizationToken = PreferenceUtils.getString(this, PreferenceUtils.KEY_AUTH_TOKEN);
        boolean debug = false;

        if (debug || userId.isEmpty() || userAccount.isEmpty() || authorizationToken.isEmpty()) {
            try {
                startActivityForResult(new Intent(this, SignUpActivity.class), REQUEST_CODE_EMAIL);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, getString(R.string.general_error), Toast.LENGTH_LONG).show();
                finish();
            }
        } else { // User already logged-in
            MainApplication.userAccount = userAccount;
            MainApplication.userPassowrd = userPassword;
            GcmHelper.start(getApplicationContext()); // Register to GCM

            Log.i(TAG, "User email: " + userAccount);
            Log.i(TAG, "User id: " + userId);
            Log.i(TAG, "authorizationToken: " + authorizationToken);
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // Position of the logout button
        String[] menuOptions = getResources().getStringArray(R.array.nav_drawer_options);
        FragmentManager fragmentManager = getSupportFragmentManager();
        mTitle = menuOptions[position];
        selectedOption = position;

        ActionBar actionBar = getSupportActionBar();
        // set action bar title if it exists and the user isn't trying to log off
        if (actionBar != null) {
            actionBar.setTitle(mTitle);
        }
        switch (position) {

            case 0: // Map
                fragmentManager.beginTransaction().replace(R.id.container, new MapViewFragment(), TAG_MAP_FRAGMENT).commit();
                break;

            case 1: // Places
                fragmentManager.beginTransaction().replace(R.id.container, new PlacesFragment(), TAG_PLACES_FRAGMENT).commit();
                break;

            case 2: // Contacts
                fragmentManager.beginTransaction().replace(R.id.container, new ContactsFragment(), TAG_CONTACTS_FRAGMENT).commit();
                break;

            case 3: // Settings
                fragmentManager.beginTransaction().replace(R.id.container, new PreferencesFragment(), TAG_PREFERENCES_FRAGMENT).commit();
                break;

            case 4: // About
                fragmentManager.beginTransaction().replace(R.id.container, new AboutFragment(), TAG_ABOUT_FRAGMENT).commit();
                break;

            default:
                fragmentManager.beginTransaction().replace(R.id.container, new MapViewFragment(), TAG_MAP_FRAGMENT).commit();
                break;
        }
        supportInvalidateOptionsMenu();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        menu.clear();
        if (mNavigationDrawerFragment != null && !mNavigationDrawerFragment.isDrawerOpen()) {
            if (selectedOption == 0) { // Map
                getMenuInflater().inflate(R.menu.map, menu);
                MenuItem menuItem = menu.findItem(R.id.action_visibility);
                if (menuItem != null) {
                    Log.d(TAG, "onPrepareOptionsMenu - Visible: " + MainApplication.visible);
                    if (MainApplication.visible) {
                        menuItem.setIcon(R.drawable.ic_visibility_white_48dp);
                    } else {
                        menuItem.setIcon(R.drawable.ic_visibility_off_white_48dp);
                    }
                }
            } else if (selectedOption == 2) { // Contacts screen
                getMenuInflater().inflate(R.menu.contacts, menu);
            } else if (selectedOption == -10) { // Add contacts screen
                getMenuInflater().inflate(R.menu.add_contact, menu);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        switch (id) {

            case R.id.add_contacts: // In Contacts (to add new ones)
                FragmentManager fragmentManager = getSupportFragmentManager();

                AddContactsFragment acf = new AddContactsFragment();
                acf.setContactUtils(mContactDataSource);

                fragmentManager.beginTransaction().replace(R.id.container, acf, TAG_ADD_CONTACTS_FRAGMENT).commit();
                selectedOption = -10;
                supportInvalidateOptionsMenu();
                break;

            case R.id.add_email: // In list of ALL contacts, when adding new ones.
                AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                        getString(R.string.analytics_action_click),
                        getString(R.string.analytics_label_add_email_button));
                AddContactsFragment.addContactFromEmail(this);
                break;

            case R.id.action_visibility:
                AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                        getString(R.string.analytics_action_click),
                        getString(R.string.analytics_label_visibility_toggle));
                toggleVisibility();
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleVisibility() {
        Utils.setVisibility(!MainApplication.visible, MainActivity.this);
        PreferenceUtils.setBoolean(getApplicationContext(),PreferenceUtils.KEY_SETTING_VISIBILITY, MainApplication.visible);

        if (MainApplication.visible) {
            Toast.makeText(this, getString(R.string.you_are_visible), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.you_are_invisible), Toast.LENGTH_LONG).show();
        }

        supportInvalidateOptionsMenu();
    }


    @Override
    public boolean onKeyUp(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_MENU:
                mNavigationDrawerFragment.toggleDrawer();
                return true;

            case KeyEvent.KEYCODE_BACK:
                if (selectedOption == 0) {
                    Log.i(TAG, "Exiting app because requested by user.");
                    finish();
                } else if (selectedOption == -10) { // -10 is the Add Contacts screen
                    mNavigationDrawerFragment.selectNavDrawerItem(3);    // 3 is the Contacts screen
                    return true;
                } else {
                    mNavigationDrawerFragment.selectNavDrawerItem(1);
                    return true;
                }
        }
        return super.onKeyUp(keycode, e);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        Log.d(TAG, "onActivityResult");

        if (requestCode == REQUEST_CODE_EMAIL) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Returned from sign up. Now we will show the map.");
                startServices();
                mNavigationDrawerFragment.setUserInfo();
                GcmHelper.start(getApplicationContext()); // Register to GCM

            } else {
                Log.w(TAG, "Returned from sign up. Exiting app on request.");
                finish();
            }

        } else if (requestCode == REQUEST_TERMS && resultCode == RESULT_OK) {
            Log.d(TAG, "Returned from terms. Now we will show sign up form.");
            // Terms shown and accepted.

        } else {
            Log.e(TAG, "Got - request Code: " + requestCode + ", result: " + resultCode);
            finish();
        }
    }

    public void showUserInMap(View view) { // Used in Contacts
        AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                getString(R.string.analytics_action_click),
                getString(R.string.analytics_label_avatar_show_user));
        if (view == null) {
            return;
        }
        ImageView image = (ImageView) view;
        String email = (String) image.getTag();
        showUserInMap(email);
    }

    private void showUserInMap(String email) { // Used in Contacts

        Log.d(TAG, "showUserInMap: " + email);
        MainApplication.emailBeingTracked = email;
        mNavigationDrawerFragment.selectNavDrawerItem(1); // Position 1 is the Map
    }

    public void toggleIDontWantToSee(View view) {
        AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                getString(R.string.analytics_action_click),
                getString(R.string.analytics_label_show_on_map_checkbox));
        if (view == null) {
            return;
        }
        CheckBox checkBox = (CheckBox) view;
        Boolean allow = checkBox.isChecked();
        String email = (String) checkBox.getTag();
        Log.d(TAG, "toggleIDontWantToSee: " + email + ", Checkbox is: " + allow);
        if (!allow) {
            try {
                MainApplication.iDontWantToSee.put(email, 1);
                PreferenceUtils.setString(this, PreferenceUtils.KEY_I_DONT_WANT_TO_SEE, MainApplication.iDontWantToSee.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (MainApplication.iDontWantToSee.has(email)) {
            MainApplication.iDontWantToSee.remove(email);
            PreferenceUtils.setString(this, PreferenceUtils.KEY_I_DONT_WANT_TO_SEE, MainApplication.iDontWantToSee.toString());
        }
    }

    public void toggleUserCanSeeMe(View view) { // Used in Contacts
        AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                getString(R.string.analytics_action_click),
                getString(R.string.analytics_label_can_see_me_checkbox));
        if (view != null) {
            CheckBox checkBox = (CheckBox) view;
            Boolean allow = checkBox.isChecked();
            String email = (String) checkBox.getTag();
            Log.d(TAG, "toggleUserCanSeeMe: " + email + ", Checkbox is: " + allow);
            if (!allow) {
                ServerApi.disallowUser(this, email);
            } else {
                try {
                    ServerApi.allowPeople(this, email);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private BroadcastReceiver exitMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "exitMessageReceiver onReceive");

            LocationService.stop(MainActivity.this.getApplicationContext());
            DataService.stop(MainActivity.this.getApplicationContext());

            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
            alertDialog.setTitle(getString(R.string.app_name));
            String message = getString(R.string.security_sign_up, MainApplication.userAccount,MainApplication.userPassowrd);
            alertDialog.setMessage(message)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            finish();
                        }
                    })
                    .setCancelable(false);
            alertDialog.show();
        }
    };

    private BroadcastReceiver switchToMapReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.beginTransaction().replace(R.id.container, new MapViewFragment(), TAG_MAP_FRAGMENT).commit();
            mNavigationDrawerFragment.selectNavDrawerItem(1);    // Index 1 because index 0 is the list view header...
        }
    };

    // For dependency injection
    public void setContactUtils(ContactDataSource contactDataSource) {
        this.mContactDataSource = contactDataSource;
    }

    public void logout(){
        final MainActivity main = this;
        new AlertDialog.Builder(main)
                .setIcon(R.drawable.ic_power_settings_new_black_48dp)
                .setMessage(R.string.confirm_logout)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which){
                        //Clear logged in status
                        PreferenceUtils.setString(main, PreferenceUtils.KEY_USER_ACCOUNT, null);
                        PreferenceUtils.setString(main, PreferenceUtils.KEY_USER_ID, null);
                        PreferenceUtils.setString(main, PreferenceUtils.KEY_AUTH_TOKEN, null);
                        MainApplication.userAccount = null;
                        MainApplication.dashboard = null;
                        MainApplication.contacts = null;
                        MainApplication.mapping = null;
                        MainApplication.places = null;
                        //Restart main activity to clear state
                        main.recreate();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

}
