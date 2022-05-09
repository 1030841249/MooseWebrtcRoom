package com.zdh.moosewebrtcroom.activity;

import static org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.zdh.moosewebrtcroom.Utils;
import com.zdh.moosewebrtcroom.databinding.ActivityChatRoomBinding;
import com.zdh.moosewebrtcroom.webrtc.WebRTCManager;
import com.zdh.moosewebrtcroom.webrtc.socket.WebSocketManager;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRoomActivity extends AppCompatActivity {
    ActivityChatRoomBinding binding;
    VideoTrack videoTrack;
    EglBase eglBase;
    private Map<String, SurfaceViewRenderer> videoViews = new HashMap<>();
    //房间里所有的ID列表
    private List<String> persons = new ArrayList<>();

    public static void launchChatRoom(Context context) {
        context.startActivity(new Intent(context, ChatRoomActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatRoomBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        eglBase = EglBase.create();
        // nginx 连接成功后，和webrtc通讯
        WebRTCManager.getIntance().joinRoom(this, eglBase);

    }

    //    远端的流数据
    public void onAddRemoteStream(MediaStream stream, String userId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addView(stream,userId);
            }
        });
    }

    public void onSetLocalStream(MediaStream mediaStream, String userId) {
        if (mediaStream.videoTracks.size() > 0) {
            videoTrack = mediaStream.videoTracks.get(0);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(!videoViews.containsKey(userId)) {
                    addView(mediaStream, userId);
                }
            }
        });
    }

    public void addView(MediaStream mediaStream, String userId) {
        // webrtc 封装好的 显示控件
        SurfaceViewRenderer surfaceViewRenderer = new SurfaceViewRenderer(this);
        surfaceViewRenderer.init(eglBase.getEglBaseContext(), null);
        surfaceViewRenderer.setScalingType(SCALE_ASPECT_FIT);
        surfaceViewRenderer.setMirror(true);
        // 设置要显示的数据源
        if (mediaStream.videoTracks.size() > 0) {
            mediaStream.videoTracks.get(0).addSink(surfaceViewRenderer);
        }
        videoViews.put(userId, surfaceViewRenderer);
        persons.add(userId);
        binding.frameLayout.addView(surfaceViewRenderer);
        int size = videoViews.size();
        for (int i = 0; i < size; i++) {
//            surfaceViewRenderer  setLayoutParams
            String peerId = persons.get(i);
            SurfaceViewRenderer renderer1 = videoViews.get(peerId);
            if (renderer1 != null) {
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                layoutParams.height = Utils.getWidth(this, size);
                layoutParams.width = Utils.getWidth(this, size);
                layoutParams.leftMargin = Utils.getX(this, size, i);
                layoutParams.topMargin = Utils.getY(this, size, i);
                renderer1.setLayoutParams(layoutParams);
            }
        }
    }
}