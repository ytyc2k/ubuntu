package fm.icelink.chat.websync4;

import org.json.JSONObject;

import fm.SingleAction;
import fm.icelink.Connection;
import fm.icelink.ConnectionCollection;
import fm.icelink.Future;
import fm.icelink.FutureState;
import fm.icelink.Guid;
import fm.icelink.IAction1;
import fm.icelink.IAction2;
import fm.icelink.IFunction1;
import fm.icelink.Promise;
import fm.icelink.Serializer;
import fm.icelink.websync4.ClientExtensions;
import fm.icelink.websync4.LeaveConferenceArgs;
import fm.icelink.websync4.LeaveConferenceFailureArgs;
import fm.icelink.websync4.LeaveConferenceSuccessArgs;
import fm.icelink.websync4.PeerClient;
import fm.websync.BackoffArgs;
import fm.websync.BindArgs;
import fm.websync.BindFailureArgs;
import fm.websync.BindSuccessArgs;
import fm.websync.Client;
import fm.websync.ConnectArgs;
import fm.websync.ConnectFailureArgs;
import fm.websync.ConnectSuccessArgs;
import fm.websync.DisconnectArgs;
import fm.websync.DisconnectCompleteArgs;
import fm.websync.PublishArgs;
import fm.websync.Record;
import fm.websync.RetryBackoffCallback;
import fm.websync.StreamFailureArgs;
import fm.websync.SubscribeArgs;
import fm.websync.SubscribeFailureArgs;
import fm.websync.SubscribeReceiveArgs;
import fm.websync.SubscribeSuccessArgs;
import fm.websync.UnbindArgs;
import fm.websync.UnbindFailureArgs;
import fm.websync.UnbindSuccessArgs;

/**
 * Signalling
 *
 * Provides abstract method doJoinAsync.
 * Provides concrete methods joinAsync and leaveAsync.
 *
 * See the Advanced Topics Manual Signalling Guide for more info.
 */
public abstract class Signalling {
    protected String sessionId;
    protected String serverUrl;
    protected String userName;
    protected String userId;
    protected String sessionChannel;
    protected String metadataChannel;
    protected String userIdKey = "userId";
    protected String userNameKey = "userName";
    protected String textMessageKey = "textMsg";
    protected IFunction1<PeerClient, Connection> createConnection;
    private IAction2<String, String> onReceivedText;
    protected Client client;
    protected ConnectionCollection connections;

    public Signalling(String serverUrl, String sessionId, String userName, IFunction1<PeerClient, Connection> createConnection, IAction2<String, String>  onReceivedText) {
        this.serverUrl = serverUrl;
        this.sessionId = sessionId;
        this.userName = userName;
        this.createConnection = createConnection;
        userId = Guid.newGuid().toString();
        this.onReceivedText = onReceivedText;

        defineChannels();
    }

    protected abstract void defineChannels();

    private void closeAllConnections() {
        for (Connection connection: connections.getValues()) {
            connection.close();
        }
        connections.removeAll();
    }

    protected Future<Object> bindUserUserMetadata(String key, String value) {
        final Promise<Object> promise = new Promise<Object>();
        try {
            client.bind(new BindArgs(new Record(key, Serializer.serializeString(value))){{
                setOnSuccess(new fm.SingleAction<BindSuccessArgs>() {
                    public void invoke(BindSuccessArgs e) {
                        promise.resolve(null);
                    }
                });
                setOnFailure(new fm.SingleAction<BindFailureArgs>() {
                    public void invoke(BindFailureArgs e) {
                        promise.reject(e.getException());
                    }
                });
            }});
        }
        catch (Exception ex){
            fm.icelink.Log.error(ex.getMessage());
        }
        return promise;
    }

    /**
     * Unbind the userId from the WebSync client making it free to be used by some other user.
     *
     * @return Future
     */
    protected Future<Object> unbindUserMetadata(String key) {
        final Promise<Object> promise = new Promise<Object>();
        try {
            client.unbind(new UnbindArgs(key){{
                setOnSuccess(new fm.SingleAction<UnbindSuccessArgs>() {
                    public void invoke(UnbindSuccessArgs e) {
                        promise.resolve(null);
                    }
                });
                setOnFailure(new fm.SingleAction<UnbindFailureArgs>() {
                    public void invoke(UnbindFailureArgs e) {
                        promise.reject(e.getException());
                    }
                });
            }});
        }
        catch (Exception ex) {
            fm.icelink.Log.error(ex.getMessage());
        }

        return promise;
    }

    protected Future<Object> unsubscribeFromChannel(String channel) {
        final Promise<Object> promise = new Promise<Object>();
        try {
            ClientExtensions.leaveConference(client, new LeaveConferenceArgs(channel){{
                setOnSuccess(new IAction1<LeaveConferenceSuccessArgs>() {
                    @Override
                    public void invoke(LeaveConferenceSuccessArgs leaveConferenceSuccessArgs) {
                        promise.resolve(null);
                    }
                });
                setOnFailure(new IAction1<LeaveConferenceFailureArgs>() {
                    @Override
                    public void invoke(LeaveConferenceFailureArgs leaveConferenceFailureArgs) {
                        promise.reject(leaveConferenceFailureArgs.getException());
                    }
                });
            }});
        }
        catch (Exception e) {
            promise.reject(e);
        }

        return promise;
    }

    /**
     * Initiate the WebSync client and provides ConnectArgs including
     * OnSuccess and OnFailure callbacks. The OnSuccess callback will
     * call to the abstract doJoinAsync method, while the OnFailure
     * callback simply rejects the Promise around successful connection.
     *
     * @return Future
     */
    public Future<Object> joinAsync() {
        final Promise<Object> promise = new Promise<Object>();
        try {
            connections = new ConnectionCollection();
            //Create the signalling client and connect
            client = new Client(serverUrl);
            client.connect(new ConnectArgs()
           {{
               setOnSuccess(new fm.SingleAction<ConnectSuccessArgs>() {
                   public void invoke(ConnectSuccessArgs e) {
                       subscribeToMetadataChannel().then(new IAction1<Object>() {
                           @Override
                           public void invoke(Object o) {
                               doJoinAsync(promise);
                           }
                       });
                   }
               });
               setOnFailure(new fm.SingleAction<ConnectFailureArgs>() {
                   public void invoke(ConnectFailureArgs e) {
                       if (promise.getState() == FutureState.Pending) {
                           promise.reject(e.getException());
                       }
                   }
               });
               setOnStreamFailure(new fm.SingleAction<StreamFailureArgs>() {
                   public void invoke(StreamFailureArgs e) {
                       closeAllConnections();
                   }
               });

                /*
                 Called after OnFailure: Used to override the client's default backoff.
                 By default the backoff doubles after each failure.
                 For example purposes that gets too long.
                 */
               setRetryBackoff(new RetryBackoffCallback() {
                   public Integer invoke(BackoffArgs e) {
                       return 1000;  // milliseconds
                   }
               });
           }}
            );
        }
        catch (Exception ex){
            if (promise.getState() == FutureState.Pending) {
                promise.reject(ex);
            }
        }
        return promise;
    }

    /**
     * Close all the existing connections and disconnect the client.
     *
     * @return Future
     */
    public Future<Object> leaveAsync() {
        final Promise<Object> promise = new Promise<Object>();

        closeAllConnections();

        try {
            //Disconnect the signalling client
            client.disconnect(new DisconnectArgs(){{
                setOnComplete(new SingleAction<DisconnectCompleteArgs>(){
                    @Override
                    public void invoke(DisconnectCompleteArgs p) {
                        promise.resolve(null);
                    }
                });
            }});
        }
        catch (Exception ex) {
            if (promise.getState() == FutureState.Pending) {
                promise.reject(ex);
            }
        }

        return promise;
    }

    protected Future<Object> subscribeToMetadataChannel() {
        final Promise promise = new Promise();
        try {
            client.subscribe(new SubscribeArgs(metadataChannel) {{
                setOnSuccess(new SingleAction<SubscribeSuccessArgs>() {
                    public void invoke(SubscribeSuccessArgs successArgs) {
                        promise.resolve(null);
                    }
                });
                setOnFailure(new SingleAction<SubscribeFailureArgs>() {
                    public void invoke(SubscribeFailureArgs failureArgs) {
                        promise.reject(failureArgs.getException());
                    }
                });
                setOnReceive(new SingleAction<SubscribeReceiveArgs>() {
                    public void invoke(SubscribeReceiveArgs receiveArgs) {
                        try {
                            if (!receiveArgs.getWasSentByMe()) {
                                JSONObject dataJson = new JSONObject(receiveArgs.getDataJson());
                                onReceivedText.invoke(dataJson.getString(userNameKey), dataJson.getString(textMessageKey));
                            }
                        }
                        catch(Exception e) {
                            fm.icelink.Log.error("Error handling incoming text message.", e);
                        }
                    }
                });
            }});
        }
        catch (Exception e) {
            if (promise.getState().equals(FutureState.Pending)) {
                promise.reject(e);
            }
        }
        return promise;
    }

    public void writeLine(String message)
    {
        try {
            JSONObject json = new JSONObject();
            json.put(userNameKey, userName);
            json.put(textMessageKey, message);
            client.publish(new PublishArgs(metadataChannel, json.toString()));
        }
        catch (Exception e) {
            fm.icelink.Log.error("Failed to send text message.", e);
        }
    }

    protected abstract void doJoinAsync(Promise<Object> promise);
    public abstract void reconnect(PeerClient remoteClient, Connection connection);
}
