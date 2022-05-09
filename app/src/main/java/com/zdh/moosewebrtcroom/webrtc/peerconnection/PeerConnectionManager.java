package com.zdh.moosewebrtcroom.webrtc.peerconnection;

import android.util.Log;

import com.zdh.moosewebrtcroom.activity.ChatRoomActivity;
import com.zdh.moosewebrtcroom.webrtc.socket.WebSocketManager;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * author: ZDH
 * Date: 2022/4/26
 * Description: 打洞，NAT穿越
 */
public class PeerConnectionManager {

    private final String TAG = "WebSocketManager";

    PeerConnectionFactory factory;
    WebSocketManager webSocketManager;
    EglBase eglBase;
    private boolean isVideoEnabled;
    private String userId;
    ChatRoomActivity context;
    ExecutorService mExecutors;

    private MediaStream mediaStream;

    private List<String> connectionIds;
    private Map<String,Peer> connectionIdDic;
    private List<PeerConnection.IceServer> iceServers;
    private Role mRole;


    // 呼叫方和被呼叫方
    private enum Role {
        Caller,Receiver
    }

    /**
     *
     * @param turnIp turn 服务器信息
     * @param userName turn 服务器用户名
     * @param password 密码
     */
    public PeerConnectionManager(String turnIp,String userName,String password) {
        mExecutors = Executors.newSingleThreadExecutor();
        iceServers = new ArrayList<>();
        connectionIds = new ArrayList<>();
        connectionIdDic = new HashMap<>();
        // 连接 turn 服务器
        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("turn:" + turnIp + ":3478?transport=udp")
                .setUsername(userName)
                .setPassword(password)
//                .setUsername("ddssingsong")
//                .setPassword("123456")
                .createIceServer();
        iceServers.add(iceServer);
    }

    public void initContext(ChatRoomActivity context,EglBase eglBase) {
        this.context = context;
        this.eglBase = eglBase;
    }

    public PeerConnectionFactory createConnectionFactory() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions());
        VideoEncoderFactory videoEncoderFactory = new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(),
                true,true);
        VideoDecoderFactory videoDecoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder().setOptions(options)
                .setAudioDeviceModule(
                        JavaAudioDeviceModule.builder(context)
                                .createAudioDeviceModule())
                .setVideoDecoderFactory(videoDecoderFactory)
                .setVideoEncoderFactory(videoEncoderFactory).createPeerConnectionFactory();
        return peerConnectionFactory;
    }

    public void joinRoom(WebSocketManager webSocketManager, ArrayList<String> connections, boolean isVideoEnabled, String myId) {
        userId = myId;
        connectionIds = connections;
        this.isVideoEnabled = isVideoEnabled;
        this.webSocketManager = webSocketManager;
        mExecutors.submit(new Runnable() {
            @Override
            public void run() {
                // 加入房间成功了，创建通信对象，发起链接
                // 设置音视频流和交换sdp（session description protocol）
                if (factory == null) {
                    factory = createConnectionFactory();

                    if (mediaStream == null) {
                        createMediaStream();
                    }

                    if (context != null) {
                        context.onSetLocalStream(mediaStream,userId);
                    }
                    // 和房间内的用户建立连接，此时为空
                    createPeerConnections();
                    // 给所有连接，添加当前用户的流
                    addStreams();
                    // 给房间里的人发送信息，offer，有新人进入
                    createOffers();
                }
            }
        });
    }

    private void addStreams() {
        for (Map.Entry<String, Peer> stringPeerEntry : connectionIdDic.entrySet()) {
            if (mediaStream == null) {
                createMediaStream();
            }
            stringPeerEntry.getValue().peerConnection.addStream(mediaStream);
        }
    }

    /**
     * 发送消息，进行sdp信息交换
     * 通过服务器中转，发到另一端，等待另一端的 answer 回复
     */
    private void createOffers() {
        // 作为新连接，新加入的用户，是呼叫方，向房间内的所有用户发送 offer
        mRole = Role.Caller;
        for (String s : connectionIdDic.keySet()) {
            Peer peer = connectionIdDic.get(s);
            peer.peerConnection.createOffer(peer,createOfferConstraints());
        }
    }

    /**
     * sdp交换信息的约束，是否交换音频或者视频数据等
     * @return
     */
    private MediaConstraints createOfferConstraints() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(isVideoEnabled)));
        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    /**
     * 同房间内所有人建立连接
     */
    private void createPeerConnections() {
        for (String connectionId : connectionIds) {
            connectionIdDic.put(connectionId, new Peer(connectionId));
        }
    }

    /**
     * 有人离开房间
     * @param socketId
     */
    public void onRemovePeer(String socketId) {

    }

    /**
     * 呼叫方收到被呼叫方的sdp
     * @param socketId
     * @param sdp
     */
    public void onReceiveAnswer(String socketId, String sdp) {
        mExecutors.submit(new Runnable() {
            @Override
            public void run() {
                Peer peer = connectionIdDic.get(socketId);
                if(peer != null) {
                    SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                    peer.peerConnection.setRemoteDescription(peer, sessionDescription);
                }
            }
        });
    }

    /**
     * 被呼叫方收到呼叫方的sdp
     * @param socketId
     * @param sdp
     */
    public void onReceiveOffer(String socketId, String sdp) {
        mExecutors.submit(new Runnable() {
            @Override
            public void run() {
                // 房间有新人进入，作为被呼叫方，需要回复，即 answer
                mRole = Role.Receiver;
                Peer peer = connectionIdDic.get(socketId);
                if (peer != null) {
                    SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.OFFER,sdp);
                    peer.peerConnection.setRemoteDescription(peer,sessionDescription);
                }
            }
        });
    }

    public void onRemoteIceCandidate(String socketId, IceCandidate iceCandidate) {
        mExecutors.submit(new Runnable() {
            @Override
            public void run() {
                Peer peer = connectionIdDic.get(socketId);
                if (peer != null) {
                    peer.peerConnection.addIceCandidate(iceCandidate);
                }
            }
        });
    }

    public void onNewPeerJoinRoom(String socketId) {
        mExecutors.submit(new Runnable() {
            @Override
            public void run() {
                if (mediaStream == null) {
                    createMediaStream();
                }
                if (!connectionIdDic.containsKey(socketId)) {
                    Peer peer = new Peer(socketId);
                    peer.peerConnection.addStream(mediaStream);
                    connectionIdDic.put(socketId,peer);
                    connectionIds.add(socketId);
                }
            }
        });
    }

    public class Peer implements SdpObserver,PeerConnection.Observer {
        String userId;
        PeerConnection peerConnection;
        Peer(String id){
            userId = id;
            peerConnection = createPeerConnection();
        }


        /**
         * 建立一个n对n的连接
         * @return
         */
        public PeerConnection createPeerConnection() {
            if (factory == null) {
                factory = createConnectionFactory();
            }
            // 端到端连接的事件监听
            PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(iceServers);
            return factory.createPeerConnection(rtcConfiguration, this);
        }


        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            peerConnection.setLocalDescription(this,sessionDescription);
        }

        @Override
        public void onSetSuccess() {
            PeerConnection.SignalingState signalingState = peerConnection.signalingState();
            if (signalingState == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                if(mRole == Role.Caller) {
                    Log.e(TAG, "onSetSuccess:HAVE_LOCAL_OFFER -- sendOffer" );
                    webSocketManager.sendOffer(userId, peerConnection.getLocalDescription().description);
                } else {
                    // 被呼叫方，返回类型 answer，交换sdp以建立连接
                    Log.e(TAG, "onSetSuccess:HAVE_LOCAL_OFFER -- sendAnswer" );
                    webSocketManager.sendAnswer(userId, peerConnection.getLocalDescription().description);
                }
            } else if(signalingState == PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
                // 回复
                peerConnection.createAnswer(this,createOfferConstraints());
                Log.e(TAG, "onSetSuccess: HAVE_REMOTE_OFFER" );
            } else if(signalingState == PeerConnection.SignalingState.STABLE) {
                Log.e(TAG, "onSetSuccess: STABLE" );
                if (mRole == Role.Receiver) {
                    webSocketManager.sendAnswer(userId,peerConnection.getLocalDescription().description);
                }
            }
        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }

        /**********************ice Candidate**************************/

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.e(TAG, "onIceCandidate: " );
            webSocketManager.sendIceCandidate(userId, iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.e(TAG, "onAddStream: " );
            context.onAddRemoteStream(mediaStream,userId);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }
    }

    private void createMediaStream() {
        mediaStream = factory.createLocalMediaStream("ARDAMS");
        /* 设置总流：添加音频流和视频流，配置参数 */
        AudioSource audioSource = factory.createAudioSource(createAudioConstraints());
        AudioTrack audioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);
        mediaStream.addTrack(audioTrack);
        if (isVideoEnabled) {
            VideoCapturer videoCapturer = createVideoCapturer();
            VideoSource videoSource = factory.createVideoSource(videoCapturer.isScreencast());
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("capture_thread",
                    eglBase.getEglBaseContext());
            videoCapturer.initialize(surfaceTextureHelper,context,videoSource.getCapturerObserver());
            videoCapturer.startCapture(320,480,10);
            VideoTrack videoTrack = factory.createVideoTrack("ARDAMSv1", videoSource);
            mediaStream.addTrack(videoTrack);
        }
    }

    /**
     * 判断视频来源，camera1和camera2使用哪个
     * @return
     */
    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer;
        // 支持 camera2就用camera2
        if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator camera2Enumerator = new Camera2Enumerator(context);
            // 摄像头选择，前置or后置
            videoCapturer = createCameraCapturer(camera2Enumerator);
        } else {
            Camera1Enumerator camera1Enumerator = new Camera1Enumerator(true);
            videoCapturer = createCameraCapturer(camera1Enumerator);
        }
        return videoCapturer;
    }

    /**
     * 创建摄像头捕获类型，优先前置
     * @param enumerator
     * @return
     */
    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                return videoCapturer;
            }
        }
        for (String deviceName : deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                return videoCapturer;
            }
        }
        return null;
    }

    //    googEchoCancellation   回音消除
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    //
    //    googNoiseSuppression   噪声抑制
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

    //    googAutoGainControl    自动增益控制
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    //    googHighpassFilter     高通滤波器
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";

    /**
     * 设置要启动的参数
     * @return
     */
    private MediaConstraints createAudioConstraints() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));

        return audioConstraints;
    }


}
