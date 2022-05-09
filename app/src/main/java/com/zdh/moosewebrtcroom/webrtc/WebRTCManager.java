package com.zdh.moosewebrtcroom.webrtc;

import android.content.Context;

import com.zdh.moosewebrtcroom.activity.ChatRoomActivity;
import com.zdh.moosewebrtcroom.webrtc.peerconnection.PeerConnectionManager;
import com.zdh.moosewebrtcroom.webrtc.socket.WebSocketManager;

import org.webrtc.EglBase;

/**
 * author: ZDH
 * Date: 2022/4/26
 * Description:
 */
public class WebRTCManager {
    private PeerConnectionManager peerConnectionManager;
    private WebSocketManager webSocketManager;
    private String roomId = "";
    private EglBase mEglBase;
    private static WebRTCManager sIntance = new WebRTCManager();

    public static WebRTCManager getIntance() {
        return sIntance;
    }

    private WebRTCManager() {

    }

    /**
     * 和房间服务器建立连接
     * @param context
     */
    public void connect(Context context,String address,String roomId) {
        this.roomId = roomId;
        peerConnectionManager = new PeerConnectionManager(address,"ddssingsong","123456");
        webSocketManager = new WebSocketManager(context,peerConnectionManager);
        webSocketManager.connect("wss://" + address + "/wss");
    }

    public void joinRoom(ChatRoomActivity chatRoomActivity, EglBase eglBase) {
        mEglBase = eglBase;
        peerConnectionManager.initContext(chatRoomActivity, mEglBase);
        webSocketManager.joinRoom(roomId);
    }
}
