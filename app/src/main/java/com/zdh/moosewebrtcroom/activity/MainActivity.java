package com.zdh.moosewebrtcroom.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.os.Bundle;
import android.view.View;

import com.zdh.moosewebrtcroom.databinding.ActivityMainBinding;
import com.zdh.moosewebrtcroom.webrtc.WebRTCManager;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    ActivityMainBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initPermission();
        initListener();
    }

    private void initPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,},1);
    }

    private void initListener() {
        binding.btnJoinRoom.setOnClickListener(view -> {
            WebRTCManager.getIntance().connect(this,binding.etAddress.getText().toString(),
                    binding.etRoomId.getText().toString());
        });
    }

    @Override
    public void onClick(View view) {

    }
}