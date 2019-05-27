package fm.icelink.chat.websync4;

import fm.icelink.Connection;
import fm.icelink.Future;
import fm.icelink.FutureState;
import fm.icelink.IAction1;
import fm.icelink.IAction2;
import fm.icelink.IFunction1;
import fm.icelink.Promise;
import fm.icelink.websync4.ClientExtensions;
import fm.icelink.websync4.JoinConferenceArgs;
import fm.icelink.websync4.JoinConferenceFailureArgs;
import fm.icelink.websync4.JoinConferenceSuccessArgs;
import fm.icelink.websync4.PeerClient;

public class AutoSignalling extends Signalling {

    public AutoSignalling (String serverUrl, String sessionId, String userName, IFunction1<PeerClient, Connection> createConnection, IAction2<String, String> onReceivedText) {
        super(serverUrl, sessionId, userName, createConnection, onReceivedText);
    }

    protected void defineChannels()
    {
        sessionChannel = "/" + sessionId;
        metadataChannel = sessionChannel + "/metadata";
    }

    @Override
    protected void doJoinAsync(final Promise<Object> promise) {
        bindUserUserMetadata(userIdKey, userId).then(new IFunction1<Object, Future<Object>>() {
            @Override
            public Future<Object> invoke(Object object) {
                return bindUserUserMetadata(userNameKey, userName);
            }
        }).then(new IFunction1<Object, Future<Object>>() {
                @Override
                public Future<Object> invoke(Object object) {
                    return subscribeToSessionChannel();
                }
        }).then(new IAction1<Object>() {
                @Override
                public void invoke(Object object) {
                    if (promise.getState() == FutureState.Pending) {
                        promise.resolve(null);
                    }
                }
        }).fail(new IAction1<Exception>() {
                @Override
                public void invoke(Exception e) {
                    if (promise.getState() == FutureState.Pending) {
                        promise.reject(e);
                    }
                }
            });
    }

    private Future<Object> subscribeToSessionChannel() {
        final Promise promise = new Promise();
        try {
            ClientExtensions.joinConference(client, new JoinConferenceArgs(sessionChannel) {{

                setOnSuccess(new IAction1<JoinConferenceSuccessArgs>() {
                    public void invoke(JoinConferenceSuccessArgs successArgs) {
                        promise.resolve(null);
                    }
                });
                setOnFailure(new IAction1<JoinConferenceFailureArgs>() {
                    public void invoke(JoinConferenceFailureArgs failureArgs) {
                        promise.reject(failureArgs.getException());
                    }
                });
                setOnRemoteClient(new IFunction1<PeerClient, Connection>() {
                    @Override
                    public Connection invoke(PeerClient peerClient) {
                        Connection connection = createConnection.invoke(peerClient);
                        connections.add(connection);
                        return connection;
                    }
                });
            }});
        }
        catch (Exception e) {
            promise.reject(e);
        }

        return promise;
    }

    public void reconnect(PeerClient remoteClient, Connection connection) {
        ClientExtensions.reconnectRemoteClient(client, remoteClient, connection);
    }
}
