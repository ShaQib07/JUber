package com.shakib.juber;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DriverSettingsActivity extends AppCompatActivity {
    private EditText mNameField, mPhoneField, mCarField;
    private ImageView mProfileImage;
    private Button mDiscard, mConfirm;
    private RadioGroup mRadioGroup;

    private FirebaseAuth mAuth;
    private DatabaseReference mDriversDatabase;

    private String userID, mName, mPhone, mCar, mService, mProfileImageUrl;

    private Uri resultUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mNameField = findViewById(R.id.name_et);
        mPhoneField = findViewById(R.id.phone_et);
        mCarField = findViewById(R.id.car_et);
        mCarField.setVisibility(View.VISIBLE);

        mRadioGroup = findViewById(R.id.radio_group);
        mRadioGroup.setVisibility(View.VISIBLE);

        mProfileImage = findViewById(R.id.profile_img);

        mDiscard = findViewById(R.id.discard_btn);
        mConfirm = findViewById(R.id.confirm_btn);

        mAuth = FirebaseAuth.getInstance();
        userID = mAuth.getCurrentUser().getUid();

        mDriversDatabase = FirebaseDatabase.getInstance().getReference(FirebaseEndPoints.USERS).child(FirebaseEndPoints.DRIVERS).child(userID);

        getUserInfo();

        mProfileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 1);
            }
        });

        mDiscard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserInformation();
            }
        });

    }

    private void getUserInfo() {
        mDriversDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("Name") != null){
                        mName = map.get("Name").toString();
                        mNameField.setText(mName);
                    }
                    if (map.get("Phone") != null){
                        mPhone = map.get("Phone").toString();
                        mPhoneField.setText(mPhone);
                    }
                    if (map.get("Car") != null){
                        mCar = map.get("Car").toString();
                        mCarField.setText(mCar);
                    }
                    if (map.get("Service") != null){
                        mService = map.get("Service").toString();
                        switch (mService){
                            case "Uber X":
                                mRadioGroup.check(R.id.uber_x);
                                break;
                            case "Uber Black":
                                mRadioGroup.check(R.id.uber_black);
                                break;
                            case "Uber XL":
                                mRadioGroup.check(R.id.uber_xl);
                                break;
                        }
                    }
                    if (map.get("ProfileImageUrl") != null){
                        mProfileImageUrl = map.get("ProfileImageUrl").toString();
                        Glide.with(getApplication())
                                .load(mProfileImageUrl)
                                .into(mProfileImage);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void saveUserInformation() {
        mName = mNameField.getText().toString();
        mPhone = mPhoneField.getText().toString();
        mCar = mCarField.getText().toString();

        int selectedId = mRadioGroup.getCheckedRadioButtonId();

        final RadioButton radioButton = findViewById(selectedId);

        if (radioButton.getText() == null){
            mService = "Uber X";
        }

        mService = radioButton.getText().toString();

        Map userInfo = new HashMap();
        userInfo.put("Name", mName);
        userInfo.put("Phone", mPhone);
        userInfo.put("Car", mCar);
        userInfo.put("Service", mService);

        mDriversDatabase.updateChildren(userInfo);

        if (resultUri != null){
            StorageReference filepath = FirebaseStorage.getInstance().getReference("Profile_images").child(userID);
            Bitmap bitmap = null;

            try {
                bitmap = MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(), resultUri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 20, baos);
            final byte[] data = baos.toByteArray();
            UploadTask uploadTask = filepath.putBytes(data);

            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(DriverSettingsActivity.this, "Oops! Image upload failed...", Toast.LENGTH_SHORT).show();

                    finish();
                }
            });

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Task<Uri> uri = taskSnapshot.getStorage().getDownloadUrl();
                    while(!uri.isComplete());
                    Uri downloadUrl = uri.getResult();

                    Map newImage = new HashMap();
                    newImage.put("ProfileImageUrl", downloadUrl.toString());
                    mDriversDatabase.updateChildren(newImage);

                    Toast.makeText(DriverSettingsActivity.this, "||Profile updated successfully||", Toast.LENGTH_SHORT).show();

                    finish();
                }
            });
        } else {
            Toast.makeText(DriverSettingsActivity.this, "||Profile updated successfully||", Toast.LENGTH_SHORT).show();

            finish();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == Activity.RESULT_OK){
            final Uri imageUri = data.getData();
            resultUri = imageUri;
            mProfileImage.setImageURI(resultUri);
        }
    }
}
