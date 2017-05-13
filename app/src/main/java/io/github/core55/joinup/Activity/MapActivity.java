/*
  Authors:Juan, Patrick, S.Stefani and Hussam
 */
package io.github.core55.joinup.Activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.core55.joinup.Api;
import io.github.core55.joinup.Entity.Meetup;
import io.github.core55.joinup.Entity.User;
import io.github.core55.joinup.Helper.OutOfBoundsHelper;
import io.github.core55.joinup.Model.AuthenticationResponse;
import io.github.core55.joinup.Model.DataHolder;
import io.github.core55.joinup.Helper.AuthenticationHelper;
import io.github.core55.joinup.Helper.GsonRequest;
import io.github.core55.joinup.Helper.HttpRequestHelper;
import io.github.core55.joinup.Helper.LocationHelper;
import io.github.core55.joinup.Helper.NavigationDrawer;
import io.github.core55.joinup.Helper.UserAdapter;
import io.github.core55.joinup.Model.UserList;
import io.github.core55.joinup.R;
import io.github.core55.joinup.Service.LocationManager;
import io.github.core55.joinup.Service.LocationService;
import io.github.core55.joinup.Service.NetworkService;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String TAG = "MapActivity";
    public static final String API_URL = "https://dry-cherry.herokuapp.com/api/";
    public static final String WEBAPP_URL = "https://culater.herokuapp.com/#/";
    private static final String WEBAPP_URL_PREFIX = "m/";
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private GoogleMap mMap;

    private LocationManager locationManager;
    private HashMap<Long, MarkerOptions> markersOnMap = new HashMap<>();

    private MarkerOptions meetupMarker;
    private Marker meetupMarkerView;

    private Double lat;
    private Double lon;

    private ArrayList userList = new ArrayList();
    private HashMap<Long, TextView> outOfBoundsIndicators = new HashMap<>();

    private RelativeLayout outOfBoundsViewGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        AuthenticationHelper.syncDataHolder(this);
        AuthenticationHelper.authenticationLogger(this);

        // Inject the navigation drawer
        NavigationDrawer.buildDrawer(this);

        // get the view wrapper
        this.outOfBoundsViewGroup = (RelativeLayout) findViewById(R.id.outOfBoundsIndicators);

        // Retrieve map hash from applink
        handleAppLink();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        LocationHelper.askLocationPermission(this);

        locationManager = new LocationManager(this);
        locationManager.start();

        createShareButtonListener();
        createPeopleButtonListener();
        createSwitchListener();

//        if (DataHolder.getInstance().getUser() != null && DataHolder.getInstance().getUser().getNickname() == null) {
//            namePrompt();
//        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        AuthenticationHelper.syncDataHolder(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener when the application is paused
        LocalBroadcastManager.getInstance(this).unregisterReceiver(networkServiceReceiver);
        // or `unregisterReceiver(networkServiceReceiver)` for a normal broadcast
        AuthenticationHelper.syncSharedPreferences(this);
    }

    // TODO: What these methods do?
    @Override
    protected void onResume() {
        super.onResume();
        // Register for the particular broadcast based on ACTION string
        IntentFilter filter = new IntentFilter(NetworkService.ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(networkServiceReceiver, filter);

        IntentFilter filter2 = new IntentFilter(LocationService.ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(locationReceiver, filter2);
        AuthenticationHelper.syncDataHolder(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        AuthenticationHelper.syncSharedPreferences(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AuthenticationHelper.syncSharedPreferences(this);
    }

    // TODO: fix
    private BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            lat = intent.getDoubleExtra("lat", -1);
            lon = intent.getDoubleExtra("lon", -1);

            if (lat != -1 && lon != -1 && (DataHolder.getInstance().getUser() != null)) {

                RequestQueue queue = Volley.newRequestQueue(MapActivity.this);
                final String url = "https://dry-cherry.herokuapp.com/api/users/" + DataHolder.getInstance().getUser().getId();

                User user = new User(lon, lat);
                user.setNickname(DataHolder.getInstance().getUser().getNickname());

                GsonRequest<User> request = new GsonRequest<>(
                        Request.Method.PATCH, url, user, User.class,

                        new Response.Listener<User>() {

                            @Override
                            public void onResponse(User user) {
                                DataHolder.getInstance().setUser(user);
                            }
                        },

                        new Response.ErrorListener() {

                            @Override
                            public void onErrorResponse(VolleyError error) {
                                HttpRequestHelper.handleErrorResponse(error.networkResponse, MapActivity.this);
                            }
                        });
                queue.add(request);
            }

        }
    };

    // Define the callback for what to do when message is received
    private BroadcastReceiver networkServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Meetup m = DataHolder.getInstance().getMeetup();
            if (m != null) {
                if (meetupMarker == null && meetupMarkerView == null && m.getPinLatitude() != null && m.getPinLongitude() != null) {
                    meetupMarker = new MarkerOptions().draggable(true);
                    meetupMarker.position(new LatLng(m.getPinLatitude(), m.getPinLongitude()));
                    meetupMarker.icon(BitmapDescriptorFactory.fromResource(R.drawable.pin_meetup));
                    meetupMarkerView = mMap.addMarker(meetupMarker);
                } else if (meetupMarker != null && meetupMarkerView != null && m.getPinLatitude() != null && m.getPinLongitude() != null) {
                    meetupMarker.position(new LatLng(m.getPinLatitude(), m.getPinLongitude()));
                    meetupMarkerView.setPosition(new LatLng(m.getPinLatitude(), m.getPinLongitude()));
                }
            }

            List<User> users = DataHolder.getInstance().getUserList();
            for (User u : users) {
                if (u.getNickname() != null) {
                }
                if (markersOnMap.containsKey(u.getId())) {
                    MarkerOptions marker = markersOnMap.get(u.getId());
                    marker.position(new LatLng(u.getLastLatitude(), u.getLastLongitude()));
                    marker.title(u.getNickname());
                } else {
                    MarkerOptions newMarker = new MarkerOptions();
                    newMarker.position(new LatLng(u.getLastLatitude(), u.getLastLongitude()));
                    newMarker.title(u.getNickname());
                    if (newMarker.getTitle() == null) {
                        newMarker.icon(BitmapDescriptorFactory.fromResource(R.drawable.pin_default));
                    } else {
                        newMarker.icon(BitmapDescriptorFactory.fromResource(R.drawable.pin_default));
                    }
                    markersOnMap.put(u.getId(), newMarker);
                    mMap.addMarker(newMarker);
                }
            }
        }
    };

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        Meetup meetup = DataHolder.getInstance().getMeetup();
        if (meetup != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(meetup.getCenterLatitude(), meetup.getCenterLongitude()),
                    meetup.getZoomLevel()));

            if (meetupMarker == null && meetupMarkerView == null
                    && meetup.getPinLatitude() != null && meetup.getPinLongitude() != null) {
                meetupMarker = new MarkerOptions().draggable(true);
                meetupMarker.position(new LatLng(meetup.getPinLatitude(), meetup.getPinLongitude()));
                meetupMarker.icon(BitmapDescriptorFactory.fromResource(R.drawable.pin_meetup));
                meetupMarkerView = mMap.addMarker(meetupMarker);
            }
        }

        try {
            // Customise map styling via json
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_styles));

            if (!success) {
                Log.e(TAG, "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }

        // disable rotation gestures, because they are not reflected in the bounds
        // of the visible area. avoids false out of bound indicators.
        mMap.getUiSettings().setRotateGesturesEnabled(false);

        // show blue dot on map
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        if (DataHolder.getInstance().getUser() != null && DataHolder.getInstance().getUser().getNickname() != null) {
            Context context = getApplicationContext();
            CharSequence text = "Welcome " + DataHolder.getInstance().getUser().getNickname() + "!";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }

        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {

            }

            @Override
            public void onMarkerDrag(Marker marker) {

            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                Log.d(TAG, "marker drag end");
                sendMeetupPinLocation(marker.getPosition().longitude, marker.getPosition().latitude);
            }
        });

        launchNetworkService();

        // TODO: fix user list
//        importUsers();

        // User out of bounds indicators
        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
            @Override
            public void onCameraMove() {
                LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
                DataHolder data = DataHolder.getInstance();
                CameraPosition camera = mMap.getCameraPosition();

                // skip if no data available
                if (data.getMeetup() == null) {
                    return;
                }
                List<User> userList = data.getUserList();

                // update indicator for each user
                for (Map.Entry<Long, MarkerOptions> item : markersOnMap.entrySet()) {
                    MarkerOptions marker = item.getValue();
                    User user = null;
                    for (User temp : userList) {
                        if (temp.getId() == item.getKey()) {
                            user = temp;
                        }
                    }

                    // if marker visible, remove indicator if present
                    if (bounds.contains(marker.getPosition())) {
                        if (outOfBoundsIndicators.containsKey(item.getKey())) {
                            TextView indicator = outOfBoundsIndicators.get(item.getKey());
                            indicator.setVisibility(View.GONE);
                        }

                        continue;
                    }
                    ;

                    String nickname = user != null ? user.getNickname() : "Anonymous";

                    // create indicator if not already present
                    if (!outOfBoundsIndicators.containsKey(item.getKey())) {
                        TextView indicator = OutOfBoundsHelper.generatePositionIdicator(nickname, View.generateViewId(), getBaseContext());
                        outOfBoundsIndicators.put(item.getKey(), indicator);
                        outOfBoundsViewGroup.addView(indicator);
                    }

                    // reposition and update indicator
                    TextView indicator = outOfBoundsIndicators.get(item.getKey());
                    indicator.setText(nickname);
                    OutOfBoundsHelper.setIndicatorPosition(bounds, marker, indicator, camera);
                    indicator.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void handleAppLink() {
        Intent appLinkIntent = getIntent();
        Uri appLinkData = appLinkIntent.getData();

        if (appLinkData != null && appLinkData.isHierarchical()) {
            String uri = appLinkIntent.getDataString();
            Log.d(TAG, "url = " + uri);

            Pattern pattern = Pattern.compile("/\\#/" + WEBAPP_URL_PREFIX + "(.*)");
            Matcher matcher = pattern.matcher(uri);
            if (matcher.find()) {
                String applinkHash = matcher.group(1);
                fetchMeetup(applinkHash);
                fetchUserList(applinkHash);
                Log.d(TAG, "hash = " + applinkHash);

                User user;
                if (DataHolder.getInstance().isAuthenticated() || DataHolder.getInstance().isAnonymous()) {
                    user = DataHolder.getInstance().getUser();
                } else {
                    // TODO: Handle error if coordinates are not available
                    user = new User(lon, lat);
                    DataHolder.getInstance().setAnonymous(true);
                }

                linkUserToMeetup(user, applinkHash);
            }
        }
    }

    /**
     * A name prompt is displayed to the non-registered users
     */
    private void namePrompt() {

        // Build modal to fill in user nickname
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(MapActivity.this);
        View mView = getLayoutInflater().inflate(R.layout.dialog_name, null);
        mBuilder.setView(mView);

        final EditText name = (EditText) mView.findViewById(R.id.enter_name);

        // Prompt modal
        final AlertDialog dialog = mBuilder.create();

        // TODO: Introduce validation
        if (name.getText().toString().matches("")) {
            dialog.show();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.setCanceledOnTouchOutside(false);
            dialog.setCancelable(false);

            Button enter = (Button) dialog.findViewById(R.id.enter);
            enter.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    String nickname = name.getText().toString();
                    Log.d(TAG, nickname);

                    patchNickname(nickname);
                    dialog.dismiss();
                }

            });
        }

        // Prompt for keyboard
        name.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });
    }

    /**
     * The nicknames are updated in the database
     *
     * @param nickname is the inputed nickname
     */
    public void patchNickname(String nickname) {
        RequestQueue queue = Volley.newRequestQueue(MapActivity.this);
        final String url = API_URL + "users/" + DataHolder.getInstance().getUser().getId();

        User user = new User();
        user.setNickname(nickname);

        GsonRequest<User> request = new GsonRequest<>(
                Request.Method.PATCH, url, user, User.class,

                new Response.Listener<User>() {

                    @Override
                    public void onResponse(User user) {
                        DataHolder.getInstance().setUser(user);
                        AuthenticationHelper.syncSharedPreferences(MapActivity.this);
                    }
                },

                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        HttpRequestHelper.handleErrorResponse(error.networkResponse, MapActivity.this);
                    }
                });
        queue.add(request);
    }

    /**
     * when clicking on the share button, a dialog is built
     * the dialog shows the
     */
    private void createShareButtonListener() {
        ImageButton mShowDialog = (ImageButton) findViewById(R.id.imageButton);
        mShowDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder mBuilder = new AlertDialog.Builder(MapActivity.this);
                View mView = getLayoutInflater().inflate(R.layout.dialog_share, null);
                mBuilder.setView(mView);

                EditText url = (EditText) mView.findViewById(R.id.location_search_field);
                String meetupLink = WEBAPP_URL + WEBAPP_URL_PREFIX + DataHolder.getInstance().getMeetup().getHash();
                url.setText(meetupLink);

                final AlertDialog dialog = mBuilder.create();
                dialog.show();
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
    }

    /**
     * The switch is responsible for controlling the location update
     * The switch is colored-green by default which indicates that the updates are on.
     */
    private void createSwitchListener() {
        Switch toggle = (Switch) findViewById(R.id.toggleSwitch);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    //enable location for users
                    locationManager.restart();
                } else {
                    //disable location for users
                    locationManager.stop();
                }
            }
        });
    }

    /**
     * The method is used to copy the provided link to the clipboard when clicking on the copy button
     *
     * @param v is the current view
     */
    public void copyToCliboard(View v) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String meetupLink = WEBAPP_URL + WEBAPP_URL_PREFIX + DataHolder.getInstance().getMeetup().getHash();
        ClipData clip = ClipData.newPlainText("label", meetupLink);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Link is copied!", Toast.LENGTH_SHORT).show();
    }

    public void launchNetworkService() {
        // Construct our Intent specifying the Service
        Intent i = new Intent(this, NetworkService.class);

        // Start the service
        startService(i);
    }

    private void fetchMeetup(String hash) {
        RequestQueue queue = Volley.newRequestQueue(this);
        final String url = API_URL + "meetups/" + hash;

        GsonRequest<Meetup> request = new GsonRequest<>(
                Request.Method.GET, url, Meetup.class,

                new Response.Listener<Meetup>() {

                    @Override
                    public void onResponse(Meetup meetup) {
                        DataHolder.getInstance().setMeetup(meetup);

                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(meetup.getCenterLatitude(), meetup.getCenterLongitude()),
                                meetup.getZoomLevel()));

                        if (meetupMarker == null && meetupMarkerView == null
                                && meetup.getPinLatitude() != null && meetup.getPinLongitude() != null) {
                            meetupMarker = new MarkerOptions().draggable(true);
                            meetupMarker.position(new LatLng(meetup.getPinLatitude(), meetup.getPinLongitude()));
                            meetupMarker.icon(BitmapDescriptorFactory.fromResource(R.drawable.pin_meetup));
                            meetupMarkerView = mMap.addMarker(meetupMarker);
                        }
                    }
                },

                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        HttpRequestHelper.handleErrorResponse(error.networkResponse, MapActivity.this);
                    }
                });
        queue.add(request);
    }

    private void fetchUserList(String hash) {
        RequestQueue queue = Volley.newRequestQueue(this);
        final String url = API_URL + "meetups/" + hash + "/users";

        GsonRequest<UserList> request = new GsonRequest<>(
                Request.Method.GET, url, UserList.class,

                new Response.Listener<UserList>() {

                    @Override
                    public void onResponse(UserList userList) {
                        DataHolder.getInstance().setUserList(userList.getUsers());
                    }
                },

                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        HttpRequestHelper.handleErrorResponse(error.networkResponse, MapActivity.this);
                    }
                });
        queue.add(request);
    }

    private void createPeopleButtonListener() {
        final ImageButton mShowDialog = (ImageButton) findViewById(R.id.peopleButton);
        mShowDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder mBuilder = new AlertDialog.Builder(MapActivity.this);
                UserAdapter adapter = new UserAdapter(getApplicationContext(), 0, userList);
                mBuilder.setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                View mView = getLayoutInflater().inflate(R.layout.content_user_list, null);
                mBuilder.setView(mView);
                final AlertDialog dialog = mBuilder.create();
                dialog.show();
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
    }

//    void importUsers() {
//        int method = Request.Method.GET;
//        String hash = DataHolder.getInstance().getMeetup().getHash();
//        String url = "http://dry-cherry.herokuapp.com/api/meetups/" + hash + "/users";
//        Log.e("url", url);
//        HeaderRequest retrieveUsersOnMeetupRequest = new HeaderRequest
//                (method, url, null, new Response.Listener<JSONObject>() {
//                    @Override
//                    public void onResponse(JSONObject response) {
//                        Log.e("response", response.toString());
//                        try {
//                            JSONArray usersJson = response.getJSONObject("data").getJSONObject("_embedded").getJSONArray("users");
//                            Log.e("usersJson", usersJson.toString());
//                            JSONObject userJson; //one user
//                            for (int i = 0; i < usersJson.length(); i++) {
//                                userJson = (JSONObject) usersJson.get(i);
//                                String nickname = userJson.getString("nickname");
//                                String status = userJson.getString("status");
//                                //retrieve link for picture: first google, if it doesn't exist -> gravatar, if it doesn't exist -> emoji
//                                String profileURL = userJson.getString("googlePictureURI"); //googlePictureURI gravatarURI
//                                if (profileURL == "null") {
//                                    profileURL = userJson.getString("gravatarURI");
//                                    if (profileURL == "null") {
//                                        profileURL = "emoji";
//                                    }
//                                }
//
//
//                                User u = new User(nickname, status, profileURL);
//                                userList.add(u);
//                            }
//                            Log.e("user_list", response.toString());
//                        } catch (Exception je) {
//                            je.printStackTrace();
//                        }
//
//                    }
//
//                }, new Response.ErrorListener() {
//                    @Override
//                    public void onErrorResponse(VolleyError error) {
//                        error.printStackTrace();
//                        Log.e("errorResponse", "dihvsvdh");
//                    }
//                }) {
//            @Override
//            public Map<String, String> getHeaders() throws AuthFailureError {
//                Map<String, String> params = new HashMap<String, String>();
//                params.put("Content-Type", "application/json");
//                params.put("Accept", "application/json, application/hal+json");
//                return params;
//            }
//        };
//        VolleyController.getInstance(this).addToRequestQueue(retrieveUsersOnMeetupRequest);
//    }

    private void sendMeetupPinLocation(Double pinLongitude, Double pinLatitude) {
        RequestQueue queue = Volley.newRequestQueue(MapActivity.this);
        final String url = API_URL + "meetups/" + DataHolder.getInstance().getMeetup().getHash();

        Meetup meetup = new Meetup();
        meetup.setPinLongitude(pinLongitude);
        meetup.setPinLatitude(pinLatitude);

        GsonRequest<Meetup> request = new GsonRequest<>(
                Request.Method.PATCH, url, meetup, Meetup.class,

                new Response.Listener<Meetup>() {

                    @Override
                    public void onResponse(Meetup meetup) {
                        DataHolder.getInstance().setMeetup(meetup);
                    }
                },

                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        HttpRequestHelper.handleErrorResponse(error.networkResponse, MapActivity.this);
                    }
                });
        queue.add(request);
    }


    private void linkUserToMeetup(User user, String hash) {
        RequestQueue queue = Volley.newRequestQueue(this);
        final String url = API_URL + "meetups/" + hash + "/users/save";

        GsonRequest<User> request = new GsonRequest<>(
                Request.Method.POST, url, user, User.class,

                new Response.Listener<User>() {

                    @Override
                    public void onResponse(User user) {
                        DataHolder.getInstance().setUser(user);
                        if (DataHolder.getInstance().getUser().getNickname() == null) {
                            namePrompt();
                        }
                        AuthenticationHelper.syncSharedPreferences(MapActivity.this);
                    }
                },

                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        HttpRequestHelper.handleErrorResponse(error.networkResponse, MapActivity.this);
                    }
                });
        queue.add(request);
    }
}
