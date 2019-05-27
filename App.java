package fm.icelink.chat.websync4;

import android.content.*;
import android.media.projection.MediaProjection;
import android.view.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fm.icelink.*;
import fm.icelink.android.*;
import fm.icelink.android.LayoutManager;
import fm.icelink.websync4.*;
import fm.websync.Record;
import layout.TextChatFragment;
import layout.VideoChatFragment;

public class App {

    // This flag determines the signalling mode used.
    // Note that Manual and Auto signalling do not Interop.
    private final static boolean SIGNAL_MANUALLY = false;
    private Signalling signalling;

    private OnReceivedTextListener textListener;

    private String sessionId;
    public String getSessionId() {
        return this.sessionId;
    }
    public void setSessionId(String sid) {
        this.sessionId = sid;
    }

    private String name;
    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }

    private boolean enableAudioSend;
    public boolean getEnableAudioSend() {
        return this.enableAudioSend;
    }
    public void setEnableAudioSend(boolean enable) {
        this.enableAudioSend = enable;
    }

    private boolean enableAudioReceive;
    public boolean getEnableAudioReceive() {
        return this.enableAudioReceive;
    }
    public void setEnableAudioReceive(boolean enable) {
        this.enableAudioReceive = enable;
    }

    private boolean enableVideoSend;
    public boolean getEnableVideoSend() {
        return this.enableVideoSend;
    }
    public void setEnableVideoSend(boolean enable) {
        this.enableVideoSend = enable;
    }

    private boolean enableVideoReceive;
    public boolean getEnableVideoReceive() {
        return this.enableVideoReceive;
    }
    public void setEnableVideoReceive(boolean enable) {
        this.enableVideoReceive = enable;
    }

    private boolean enableScreenShare;
    public boolean getEnableScreenShare() {
        return this.enableScreenShare;
    }
    public void setEnableScreenShare(boolean enable) {
        this.enableScreenShare = enable;
    }

    private MediaProjection mediaProjection;
    public MediaProjection getMediaProjection() {
        return this.mediaProjection;
    }
    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    private IceServer[] iceServers = new IceServer[]
    {
        new IceServer("stun:turn.frozenmountain.com:3478"),
        //NB: url "turn:turn.icelink.fm:443" implies that the relay server supports both TCP and UDP
        //if you want to restrict the network protocol, use "turn:turn.icelink.fm:443?transport=udp"
        //or "turn:turn.icelink.fm:443?transport=tcp". For further info, refer to RFC 7065 3.1 URI Scheme Syntax
        new IceServer("turn:turn.frozenmountain.com:80", "test", "pa55w0rd!"),
        new IceServer("turns:turn.frozenmountain.com:443", "test", "pa55w0rd!")
    };

    private HashMap<View, RemoteMedia> mediaTable;

    private String websyncServerUrl = "https://v4.websync.fm/websync.ashx"; // WebSync On-Demand

    private LocalMedia localMedia = null;
    private LayoutManager layoutManager = null;

    private fm.icelink.chat.websync4.AecContext aecContext;
    private boolean enableH264 = false;

    private Context context = null;
    private boolean usingFrontVideoDevice = true;

    static {
        fm.icelink.Log.setLogLevel(fm.icelink.LogLevel.Debug);
        fm.icelink.Log.setProvider(new fm.icelink.android.LogProvider(LogLevel.Debug));
    }

    private App(Context context) {
        this.context = context.getApplicationContext();

        mediaTable = new HashMap<>();

        enableAudioSend = true;
        enableAudioReceive = true;
        enableVideoSend = true;
        enableVideoReceive = true;
    }

    private static App app;

    public static synchronized App getInstance(Context context) {
        if (app == null) {
            app = new App(context);
        }
        return app;
    }

    /**
     * Convenience: allow registry for local and remote views for context menu.
     */
    public void registerAvailableViewsForContextMenu(final VideoChatFragment fragment) {
        if (fragment == null) {
            String e = "Cannot register for context menus on a null object.";
            Log.debug(e, new Exception(e));
        }

        // Register local.
        if (localMedia != null && localMedia.getView() != null) {
            fragment.registerForContextMenu((View)localMedia.getView());
        }

        // Register any remotes.
        if (mediaTable != null && !mediaTable.isEmpty()) {
            Iterator<Map.Entry<View, RemoteMedia>> i = mediaTable.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<View, RemoteMedia> e = i.next();
                fragment.registerForContextMenu(e.getKey());
            }
        }
    }

    public Future<fm.icelink.LocalMedia> startLocalMedia(final VideoChatFragment fragment) {
        return Promise.wrapPromise(new IFunction0<Future<fm.icelink.LocalMedia>>() {
            public Future<fm.icelink.LocalMedia> invoke() {
                enableH264 = false;
                if (fm.icelink.openh264.Utility.isSupported()) {
                    final String downloadPath = context.getFilesDir().getPath();
                    fm.icelink.openh264.Utility.downloadOpenH264(downloadPath).waitForResult();

                    System.load(PathUtility.combinePaths(downloadPath, fm.icelink.openh264.Utility.getLoadLibraryName()));
                    enableH264 = true;
                }

                // Set up the local media.
                aecContext = new AecContext();

                if (enableScreenShare) {
                    localMedia = new ScreenShareLocalMedia(mediaProjection, context, enableH264, !enableAudioSend, !enableVideoSend, aecContext);
                } else {
                    localMedia = new CameraLocalMedia(context, enableH264, !enableAudioSend, !enableVideoSend, aecContext);
                }

                final View localView = (View)localMedia.getView();
                // Set up the layout manager.
                fragment.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (localView != null) {
                            localView.setContentDescription("localView");
                        }
                        layoutManager = new LayoutManager(fragment.container);
                        layoutManager.setLocalView(localView);
                    }
                });

                fragment.registerForContextMenu(localView);
                localView.setOnTouchListener(fragment);

                // Start the local media.
                return localMedia.start();
            }
        });
    }

    public Future<fm.icelink.LocalMedia> stopLocalMedia() {
        return Promise.wrapPromise(new IFunction0<Future<fm.icelink.LocalMedia>>() {
            public Future<fm.icelink.LocalMedia> invoke() {
                if (localMedia == null) {
                    throw new RuntimeException("Local media has already been stopped.");
                }

                // Stop the local media.
                return localMedia.stop().then(new IAction1<fm.icelink.LocalMedia>() {
                    public void invoke(fm.icelink.LocalMedia o) {
                        LayoutManager lm = layoutManager;
                        // Tear down the layout manager.
                        if (lm != null) {
                            lm.removeRemoteViews();
                            lm.unsetLocalView();
                            layoutManager = null;
                        }

                        // Tear down the local media.
                        if (localMedia != null) {
                            localMedia.destroy(); // localMedia.destroy() will also destroy AecContext
                            localMedia = null;
                        }
                    }
                });
            }
        });
    }

    public fm.icelink.Future<Object> joinAsync(final VideoChatFragment fragment, TextChatFragment textChat) {
        textListener = textChat;
        if (SIGNAL_MANUALLY) {
            signalling = manualSignalling(fragment);
        }
        else {
            signalling = autoSignalling(fragment);
        }

        return signalling.joinAsync();
    }

    private Signalling autoSignalling(final VideoChatFragment fragment) {
        return new AutoSignalling(websyncServerUrl, getSessionId(), name, new IFunction1<PeerClient, Connection>() {
            @Override
            public Connection invoke(PeerClient remoteClient) {
                return connection(fragment, remoteClient);
            }
        }, new IAction2<String, String> () {
            @Override
            public void invoke(String n, String m) {
                textListener.onReceivedText(n, m);
            }
        });
    }
    
    private Signalling manualSignalling(final VideoChatFragment fragment) {
        return new ManualSignalling(websyncServerUrl, getSessionId(), name, new IFunction1<PeerClient, Connection>(){
            @Override
            public Connection invoke(PeerClient remoteClient) {
                return connection(fragment, remoteClient);
            }
        }, new IAction2<String, String> () {
            @Override
            public void invoke(String n, String m) {
                textListener.onReceivedText(n, m);
            }
        });
   }

   private Connection connection(final VideoChatFragment fragment, final PeerClient remoteClient) {

        String n = "Unknown";
        if (remoteClient.getBoundRecords() != null)
        {
            Record r = remoteClient.getBoundRecords().get("userName");

            if (r != null && r.getValueJson() != null)
            {
                String x = r.getValueJson();
                if(x.length() > 2)
                {
                    n = x.substring(1, x.length() - 1);
                }
            }
        }
        
       final String peerName = n;

       // Create connection to remote client.
       final RemoteMedia remoteMedia = new RemoteMedia(context, enableH264, false, false, aecContext);
       final AudioStream audioStream = new AudioStream(localMedia, remoteMedia);
       audioStream.setLocalSend(enableAudioSend);
       audioStream.setLocalReceive(enableAudioReceive);

       final VideoStream videoStream = new VideoStream(localMedia, remoteMedia);
       videoStream.setLocalSend(enableVideoSend);
       videoStream.setLocalReceive(enableVideoReceive);

       final Connection connection = new Connection(new Stream[]{audioStream, videoStream});
       connection.setIceServers(iceServers);

       if (remoteMedia.getView() != null) {
           // Add the remote view to the layout.
           remoteMedia.getView().setContentDescription("remoteView_" + remoteMedia.getId());
           layoutManager.addRemoteView(remoteMedia.getId(), remoteMedia.getView());
           mediaTable.put(remoteMedia.getView(), remoteMedia);
           fragment.registerForContextMenu(remoteMedia.getView());
           remoteMedia.getView().setOnTouchListener(fragment);
       }

       connection.addOnStateChange(new fm.icelink.IAction1<Connection>() {
           public void invoke(Connection c) {
               if (c.getState() == ConnectionState.Connected)
               {
                   textListener.onPeerJoined(peerName);
               }
               else if (c.getState() == ConnectionState.Closing ||
                       c.getState() == ConnectionState.Failing) {
                   // Remove the remote view from the layout.
                   LayoutManager lm = layoutManager;
                   if (lm != null) {
                       lm.removeRemoteView(remoteMedia.getId());
                   }
                   mediaTable.remove(remoteMedia.getView());
                   remoteMedia.destroy();
               }
               else if (c.getState() == ConnectionState.Closed) {
                   textListener.onPeerLeft(peerName);
               }
               else if (c.getState() == ConnectionState.Failed) {
                   textListener.onPeerLeft(peerName);
                   if (!SIGNAL_MANUALLY)
                       signalling.reconnect(remoteClient, c);
               }
           }
       });

       return connection;
   }

    public fm.icelink.Future<Object> leaveAsync() {
        return signalling.leaveAsync();
    }


    public void useNextVideoDevice() {
        if (localMedia != null && localMedia.getVideoSource() != null) {

            localMedia.changeVideoSourceInput(usingFrontVideoDevice ?
                    ((CameraSource) localMedia.getVideoSource()).getBackInput() :
                    ((CameraSource) localMedia.getVideoSource()).getFrontInput());

            usingFrontVideoDevice = !usingFrontVideoDevice;
        }
    }

    public Future<Object> pauseLocalVideo() {
        if (localMedia != null && !enableScreenShare) {
            VideoSource videoSource = localMedia.getVideoSource();
            if (videoSource != null) {
                if (videoSource.getState() == MediaSourceState.Started) {
                    return videoSource.stop();
                }
            }
        }
        return Promise.resolveNow();
    }

    public Future<Object> resumeLocalVideo() {
        if (localMedia != null) {
            VideoSource videoSource = localMedia.getVideoSource();
            if (videoSource != null) {
                if (videoSource.getState() == MediaSourceState.Stopped) {
                    return videoSource.start();
                }
            }
        }
        return Promise.resolveNow();
    }

    public void setIsRecordingAudio(View v, boolean record)
    {
        if (localMedia.getView() == v) {
            if (localMedia.getIsRecordingAudio() != record) {
                localMedia.toggleAudioRecording();
            }
        } else {
            RemoteMedia remote = mediaTable.get(v);
            if (remote.getIsRecordingAudio() != record) {
                remote.toggleAudioRecording();
            }
        }
    }

    public boolean getIsRecordingAudio(View v)
    {
        if (localMedia != null && localMedia.getView() != null && localMedia.getView() == v) {
            return localMedia.getIsRecordingAudio();
        }
        else if (mediaTable.get(v) != null) {
            return mediaTable.get(v).getIsRecordingAudio();
        }
        else return false;
    }

    public void setIsRecordingVideo(View v, boolean record)
    {
        if (localMedia.getView() == v) {
            if (localMedia.getIsRecordingVideo() != record) {
                localMedia.toggleVideoRecording();
            }
        } else {
            RemoteMedia remote = mediaTable.get(v);
            if (remote.getIsRecordingVideo() != record) {
                remote.toggleVideoRecording();
            }
        }
    }

    public boolean getIsRecordingVideo(View v)
    {
        if (localMedia != null && localMedia.getView() != null && localMedia.getView() == v) {
            return localMedia.getIsRecordingVideo();
        }
        else if (mediaTable.get(v) != null) {
            return mediaTable.get(v).getIsRecordingVideo();
        }
        else return false;
    }

    public void setAudioMuted(View v, boolean mute)
    {
        if (localMedia.getView() == v) {
            localMedia.setAudioMuted(mute);
        } else {
            mediaTable.get(v).setAudioMuted(mute);
        }
    }

    public boolean getAudioMuted(View v)
    {
        if (localMedia != null && localMedia.getView() != null && localMedia.getView() == v) {
            return localMedia.getAudioMuted();
        }
        else if (mediaTable.get(v) != null) {
            return mediaTable.get(v).getAudioMuted();
        }
        else return false;
    }

    public void setVideoMuted(View v, boolean mute)
    {
        if (localMedia.getView() == v) {
            localMedia.setVideoMuted(mute);
        } else {
            mediaTable.get(v).setVideoMuted(mute);
        }
    }

    public boolean getVideoMuted(View v)
    {
        if (localMedia != null && localMedia.getView() != null && localMedia.getView() == v) {
            return localMedia.getVideoMuted();
        }
        else if (mediaTable.get(v) != null) {
            return mediaTable.get(v).getVideoMuted();
        }
        else return false;
    }

    public void writeLine(String message)
    {
        signalling.writeLine(message);
    }

    public interface OnReceivedTextListener {
        void onReceivedText(String name, String message);
        void onPeerJoined(String name);
        void onPeerLeft(String name);
    }
}
