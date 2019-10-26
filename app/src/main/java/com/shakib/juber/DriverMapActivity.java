package com.shakib.juber;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private Button mLogOut, mSettings;
    private GoogleMap mMap;
    private String passengerId = "";
    private LinearLayout mPassengerInfo;
    private ImageView mPassengerProfileImage;
    private TextView mPassengerName, mPassengerPhone, mPassengerDestination;

    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    private SupportMapFragment mapFragment;

    DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference(FirebaseEndPoints.DRIVERS_AVAILABLE);
    DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference(FirebaseEndPoints.DRIVERS_WORKING);
    GeoFire geoFireAvailable = new GeoFire(refAvailable);
    GeoFire geoFireWorking = new GeoFire(refWorking);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ){
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        } else {
            mapFragment.getMapAsync(this);
        }

        mPassengerInfo = findViewById(R.id.passenger_info);

        mPassengerProfileImage = findViewById(R.id.passenger_profile_img);

        mPassengerName = findViewById(R.id.passenger_name);
        mPassengerPhone = findViewById(R.id.passenger_phone);
        mPassengerDestination = findViewById(R.id.passenger_destination);

        mLogOut = findViewById(R.id.logout_btn);
        mSettings = findViewById(R.id.settings_btn);

        mLogOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(DriverMapActivity.this, MainActivity.class));
                finish();
            }
        });

        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DriverMapActivity.this, DriverSettingsActivity.class));
            }
        });
        getAssignedPassenger();
    }

    private void getAssignedPassenger() {
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedPassengerRef = FirebaseDatabase.getInstance().getReference(FirebaseEndPoints.USERS).child(FirebaseEndPoints.DRIVERS).child(driverId).child(FirebaseEndPoints.PASSENGER_REQUEST).child(FirebaseEndPoints.PASSENGER_RIDE_ID);
        assignedPassengerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()){
                    passengerId = dataSnapshot.getValue().toString();
                    getAssignedPassengerPickupLocation();
                    getAssignedPassengerInfo();
                    getAssignedPassengerDestination();
                } else {
                    passengerId = "";
                    if (mPickupLocationMarker != null){
                        mPickupLocationMarker.remove();
                    }
                    if (assignedPassengerPickupRequestRefListener != null){
                        assignedPassengerPickupRequestRef.removeEventListener(assignedPassengerPickupRequestRefListener);
                    }
                    mPassengerInfo.setVisibility(View.GONE);
                    mPassengerName.setText("");
                    mPassengerPhone.setText("");
                    mPassengerDestination.setText("Destination: --");
                    mPassengerProfileImage.setImageResource(R.drawable.ic_account);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getAssignedPassengerDestination() {
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedPassengerDestinationRef = FirebaseDatabase.getInstance().getReference(FirebaseEndPoints.USERS).child(FirebaseEndPoints.DRIVERS).child(driverId).child(FirebaseEndPoints.PASSENGER_REQUEST).child(FirebaseEndPoints.DESTINATION);
        assignedPassengerDestinationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()){
                    String destination = dataSnapshot.getValue().toString();
                    mPassengerDestination.setText("Destination: "+destination);
                } else {
                    mPassengerDestination.setText("Destination: Not selected");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getAssignedPassengerInfo() {
        mPassengerInfo.setVisibility(View.VISIBLE);
        DatabaseReference mPassengerDatabase = FirebaseDatabase.getInstance().getReference(FirebaseEndPoints.USERS).child(FirebaseEndPoints.PASSENGERS).child(passengerId);
        mPassengerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();

                    if (map.get("Name") != null){ ;
                        mPassengerName.setText(map.get("Name").toString());
                    }
                    if (map.get("Phone") != null){
                        mPassengerPhone.setText(map.get("Phone").toString());
                    }
                    if (map.get("ProfileImageUrl") != null){
                        Glide.with(getApplication())
                                .load(map.get("ProfileImageUrl").toString())
                                .into(mPassengerProfileImage);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private Marker mPickupLocationMarker;
    private  DatabaseReference assignedPassengerPickupRequestRef;
    private ValueEventListener assignedPassengerPickupRequestRefListener;

    private void getAssignedPassengerPickupLocation() {
        assignedPassengerPickupRequestRef = FirebaseDatabase.getInstance().getReference(FirebaseEndPoints.PICKUP_REQUEST).child(passengerId).child("l");
        assignedPassengerPickupRequestRefListener = assignedPassengerPickupRequestRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && !passengerId.equals("")){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    if (map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLng = new LatLng(locationLat, locationLng);
                    mPickupLocationMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Pickup here").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ){
            return;
        }
        buildGoogleApiClient();
       mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGoogleApiClient() {

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {

        if (getApplicationContext() != null){
            mLastLocation = location;

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(14));

            switch (passengerId){
                case "":
                    geoFireWorking.removeLocation(userId);
                    geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;

                 default:
                     geoFireAvailable.removeLocation(userId);
                     geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                     break;

            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ){
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    final int LOCATION_REQUEST_CODE = 1;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case LOCATION_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this);
                } else {
                    Toast.makeText(this, "Please provide the permissions", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        geoFireAvailable.removeLocation(userId, new GeoFire.CompletionListener () {
            @Override
            public void onComplete(String key, DatabaseError error) {
                Toast.makeText(DriverMapActivity.this, "Driver offline", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
