package com.zdh.moosewebrtcroom.webrtc.socket;

import android.content.Context;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.zdh.moosewebrtcroom.activity.ChatRoomActivity;
import com.zdh.moosewebrtcroom.webrtc.peerconnection.PeerConnectionManager;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.webrtc.IceCandidate;

import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * author: ZDH
 * Date: 2022/4/26
 * Description: 连接相关
 */
public class WebSocketManager {

    private String TAG = "WebSocketManager";

    private Context mContext;
    private WebSocketClient mWebSocketClient;
    private PeerConnectionManager peerConnectionManager;
    public WebSocketManager(Context context, PeerConnectionManager peerConnectionManager) {
        mContext = context;
        this.peerConnectionManager = peerConnectionManager;
    }

    /**
     * 连接房间服务器
     */
    public void connect(String wss) {
        URI uri = URI.create(wss);
        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.e(TAG, "onOpen: ");
                ChatRoomActivity.launchChatRoom(mContext);
            }

            @Override
            public void onMessage(String message) {
                Map map = JSON.parseObject(message, Map.class);
                String eventName = (String) map.get("eventName");
                Log.e(TAG, "eventName: " + eventName);
                switch (eventName) {
                    case "_peers":
                        hanleJoinRoom(map);
                        break;
                    case "_new_peer":
                        handleNewPeerJoinRoom(map);
                        break;
                    case "_offer":
                        handleOffer(map);
                        break;
                    case "_answer":
                        handleAnswer(map);
                        break;
                    case "_ice_candidate":
                        handleRemoteCandidate(map);
                        break;
                    case "_remove_peer":
                        handleRemovePeer(map);
                        break;
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.e(TAG, "onClose: " + code + "   reason : " + reason);
            }

            @Override
            public void onError(Exception ex) {

            }
        };
        if (wss.startsWith("wss")) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new TrustManagerTest()}, new SecureRandom());
                SSLSocketFactory factory = null;
                if (sslContext != null) {
                    factory = sslContext.getSocketFactory();
                }
                if (factory != null) {
                    mWebSocketClient.setSocket(factory.createSocket());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mWebSocketClient.connect();
    }

    /**
     * 离开房间
     * @param map
     */
    private void handleRemovePeer(Map map) {
        Map data = (Map) map.get("data");
        if (data != null) {
            String socketId = (String) data.get("socketId");
            peerConnectionManager.onRemovePeer(socketId);
        }
    }


    /**
     * 新用户加入房间
     * @param map
     */
    private void handleNewPeerJoinRoom(Map map) {
        Map data = (Map) map.get("data");
        String socketId = "";
        if (data != null) {
            socketId = (String) data.get("socketId");
            peerConnectionManager.onNewPeerJoinRoom(socketId);
        }
    }

    private void hanleJoinRoom(Map<String,Object> map) {
        Map data = (Map) map.get("data");
        JSONArray arr;
        if (data != null) {
            arr = (JSONArray) data.get("connections");
            String js = com.alibaba.fastjson.JSONObject.toJSONString(arr, SerializerFeature.WriteClassName);
            ArrayList<String> connections = (ArrayList<String>) JSONObject.parseArray(js, String.class);

            String myId = (String) data.get("you");
            peerConnectionManager.joinRoom(this,connections,true, myId);

        }
    }

    public void handleAnswer(Map<String,Object> map) {
        Map data = (Map) map.get("data");
        Map sdpDic;
        if (data != null) {
            sdpDic = (Map) data.get("sdp");
            String socketId = (String) data.get("socketId");
//            对方  响应的sdp
            String sdp = (String) sdpDic.get("sdp");
            peerConnectionManager.onReceiveAnswer(socketId, sdp);
        }
    }

    /**
     * 被呼叫方，收到呼叫方的sdp信息
     * @param map
     */
    private void handleOffer(Map map) {
        Map data = (Map) map.get("data");
        if (data != null) {
            Map sdpDic = (Map) data.get("sdp");
            String socketId = (String) data.get("socketId");
            String sdp = (String) sdpDic.get("sdp");
            peerConnectionManager.onReceiveOffer(socketId,sdp);
        }
    }

    // 处理交换信息
    private void handleRemoteCandidate(Map map) {
        Map data = (Map) map.get("data");
        String socketId;
        if (data != null) {
            socketId = (String) data.get("socketId");
            String sdpMid = (String) data.get("id");
            sdpMid = (null == sdpMid) ? "video" : sdpMid;
            int sdpMLineIndex = (int) Double.parseDouble(String.valueOf(data.get("label")));
            String candidate = (String) data.get("candidate");
            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
            peerConnectionManager.onRemoteIceCandidate(socketId, iceCandidate);
        }
    }

    //请求
    public void joinRoom(String roomId) {
//        请求  http     socket 请求
        Map<String, Object> map = new HashMap<>();
        map.put("eventName", "__join");
        Map<String, String> childMap = new HashMap<>();
        childMap.put("room", roomId);
        map.put("data", childMap);
        JSONObject object = new JSONObject(map);
        final String jsonString = object.toString();
//        Log.d(TAG, "send-->" + jsonString);
        Log.d(TAG, "joinRoom");
        mWebSocketClient.send(jsonString);
    }

    public void sendOffer(String socketId, String sdp) {
        HashMap<String, Object> childMap1 = new HashMap();
        childMap1.put("type", "offer");
        childMap1.put("sdp", sdp);

        HashMap<String, Object> childMap2 = new HashMap();
        childMap2.put("socketId", socketId);
        childMap2.put("sdp", childMap1);

        HashMap<String, Object> map = new HashMap();
        map.put("eventName", "__offer");
        map.put("data", childMap2);
        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
//        Log.d(TAG, "send-->" + jsonString);
        Log.d(TAG, "sendOffer");
        mWebSocketClient.send(jsonString);
    }

    public void sendAnswer(String socketId, String sdp) {
        HashMap<String, Object> childMap1 = new HashMap();
        childMap1.put("type", "answer");
        childMap1.put("sdp", sdp);

        HashMap<String, Object> childMap2 = new HashMap();
        childMap2.put("socketId", socketId);
        childMap2.put("sdp", childMap1);

        HashMap<String, Object> map = new HashMap();
        map.put("eventName", "__answer");
        map.put("data", childMap2);
        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
//        Log.d(TAG, "send-->" + jsonString);
        Log.d(TAG, "sendAnswer");
        mWebSocketClient.send(jsonString);
    }


    public  void sendIceCandidate(String socketId, IceCandidate iceCandidate){
        HashMap<String, Object> childMap = new HashMap();
        childMap.put("id", iceCandidate.sdpMid);
        childMap.put("label", iceCandidate.sdpMLineIndex);
        childMap.put("candidate", iceCandidate.sdp);
        childMap.put("socketId", socketId);
        HashMap<String, Object> map = new HashMap();
        map.put("eventName", "__ice_candidate");
        map.put("data", childMap);
        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
//        Log.d(TAG, "send-->" + jsonString);
        Log.d(TAG, "sendIceCandidate");
        mWebSocketClient.send(jsonString);
    }
    // 忽略证书
    public static class TrustManagerTest implements X509TrustManager {


        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
