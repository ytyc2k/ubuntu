package fm.icelink.chat.websync4;

import fm.SingleAction;
import fm.icelink.Candidate;
import fm.icelink.Connection;
import fm.icelink.Future;
import fm.icelink.FutureState;
import fm.icelink.IAction1;
import fm.icelink.IAction2;
import fm.icelink.IFunction1;
import fm.icelink.Log;
import fm.icelink.Promise;
import fm.icelink.Serializer;
import fm.icelink.SessionDescription;
import fm.icelink.websync4.PeerClient;
import fm.websync.PublishArgs;
import fm.websync.PublishFailureArgs;
import fm.websync.PublishSuccessArgs;
import fm.websync.SubscribeArgs;
import fm.websync.SubscribeFailureArgs;
import fm.websync.SubscribeReceiveArgs;
import fm.websync.SubscribeSuccessArgs;
import fm.websync.subscribers.*;

/**
 * Signalling
 *
 * Provides concrete implementation for doJoinAsync.
 *
 * See the Advanced Topics Manual Signalling Guide for more info.
 */
public class ManualSignalling extends Signalling {

    private String offerTag = "offer";
    private String answerTag = "answer";
    private String candidateTag = "candidate";
    private String userChannel;

    protected Connection connectionInUserChannel;

    public ManualSignalling (String serverUrl, String sessionId, String userName, IFunction1<PeerClient, Connection> createConnection, IAction2<String, String>  onReceivedText) {
        super(serverUrl, sessionId, userName, createConnection, onReceivedText);
    }

    protected void defineChannels()
    {
        userChannel = "/user/" + userId;
        sessionChannel = "/manual-signalling/" + sessionId;
        metadataChannel = sessionChannel + "/metadata";
    }

    private String remoteUserChannel(String remoteUserId) {
        return "/user/" + remoteUserId;
    }

    /**
     * Handles subscription to the user and session channels and promise resolution/rejection.
     * @param promise The connection promise created by joinAsync method.
     */
    protected void doJoinAsync(final Promise<Object> promise) {
        bindUserUserMetadata(userIdKey, userId).then(new IFunction1<Object, Future<Object>>() {
            @Override
            public Future<Object> invoke(Object object) {
                return bindUserUserMetadata(userNameKey, userName);
            }
        }).then(new IFunction1<Object, Future<Object>>() {
            @Override
            public Future<Object> invoke(Object object) {
                return subscribeToUserChannel();
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

    private Future<Object> subscribeToUserChannel() {
        final Promise<Object> promise = new Promise<Object>();
        try {
            client.subscribe(new SubscribeArgs(userChannel){{
                setOnSuccess(new fm.SingleAction<SubscribeSuccessArgs>() {
                    public void invoke(SubscribeSuccessArgs e) {
                        promise.resolve(null);
                    }
                });
                setOnFailure(new fm.SingleAction<SubscribeFailureArgs>() {
                    public void invoke(SubscribeFailureArgs e) {
                        promise.reject(e.getException());
                    }
                });
                setOnReceive(new fm.SingleAction<SubscribeReceiveArgs>(){
                    public void invoke(SubscribeReceiveArgs e) {
                        try {
                            String remoteClientId = e.getClient().getClientId().toString();
                            final String remoteUserId = Serializer.deserializeString(e.getPublishingClient().getBoundRecords().get(userIdKey).getValueJson());

                            connectionInUserChannel = connections.getByExternalId(remoteClientId);
                            if (e.getTag().equals(candidateTag)) {
                                if (connectionInUserChannel == null) {
                                    connectionInUserChannel = createConnectionAndWireOnLocalCandidate(new PeerClient(e.getPublishingClient().getClientId().toString(), e.getPublishingClient().getBoundRecords()), remoteUserId);
                                    connectionInUserChannel.setExternalId(remoteClientId);
                                    connections.add(connectionInUserChannel);
                                }

                                Log.info("Received candidate from remote peer.");

                                connectionInUserChannel.addRemoteCandidate(Candidate.fromJson(e.getDataJson())).fail(new IAction1<Exception>() {
                                    @Override
                                    public void invoke(Exception e) {
                                        Log.error("Could not process candidate from remote peer.", e);
                                    }
                                });
                            }
                            else if (e.getTag().equals(offerTag)) {

                                Log.info("Received offer from remote peer.");

                                if (connectionInUserChannel == null) {

                                    connectionInUserChannel = createConnectionAndWireOnLocalCandidate(new PeerClient(e.getPublishingClient().getClientId().toString(), e.getPublishingClient().getBoundRecords()), remoteUserId);
                                    connectionInUserChannel.setExternalId(remoteClientId);
                                    connections.add(connectionInUserChannel);

                                    connectionInUserChannel.setRemoteDescription(SessionDescription.fromJson(e.getDataJson())).then(new IFunction1<SessionDescription, Future<SessionDescription>>() {
                                        @Override
                                        public Future<SessionDescription> invoke(SessionDescription offer) {
                                            return connectionInUserChannel.createAnswer();
                                        }
                                    }).then(new IFunction1<SessionDescription, Future<SessionDescription>>() {
                                        @Override
                                        public Future<SessionDescription> invoke(SessionDescription answer) {
                                            return connectionInUserChannel.setLocalDescription(answer);
                                        }
                                    }).then(new IAction1<SessionDescription>() {
                                        @Override
                                        public void invoke(SessionDescription answer) {
                                            try {
                                                client.publish(new PublishArgs(remoteUserChannel(remoteUserId), answer.toJson(), answerTag){{
                                                    setOnSuccess(new fm.SingleAction<PublishSuccessArgs>() {
                                                        public void invoke(PublishSuccessArgs e) {
                                                            promise.resolve(null);
                                                        }
                                                    });
                                                    setOnFailure(new fm.SingleAction<PublishFailureArgs>() {
                                                        public void invoke(PublishFailureArgs e) {
                                                            promise.reject(e.getException());
                                                        }
                                                    });
                                                }});
                                            }
                                            catch (Exception ex){
                                                Log.error(ex.getMessage());
                                            }
                                        }
                                    }).fail(new IAction1<Exception>() {
                                        @Override
                                        public void invoke(Exception e) {
                                            Log.error("Could not process offer from remote peer.", e);
                                        }
                                    });
                                }
                            }
                            else if (e.getTag().equals(answerTag)) {
                                if (connectionInUserChannel != null) {
                                    Log.info("Received answer from remote peer");

                                    connectionInUserChannel.setRemoteDescription(SessionDescription.fromJson(e.getDataJson())).fail(new IAction1<Exception>() {
                                        @Override
                                        public void invoke(Exception e) {
                                            Log.error("Could not process answer from remote peer.", e);
                                        }
                                    });
                                }
                                else {
                                    Log.error("Received answer, but connection does not exist!");
                                }
                            }
                        }
                        catch (Exception ex){
                            Log.error(ex.getMessage());
                        }
                    }
                });

            }});
        }
        catch (Exception ex){
            Log.error(ex.getMessage());
        }

        return promise;
    }

    /**
     * Handles subscribing to the session channel and connecting to subscribed clients.
     *
     * See the Subscribing to the Session Channel and Connecting to Subscribed Clients sections
     * of the Advanced Topics Manual Signalling Guide for more info.
     *
     * @return Future
     */
    private Future<Object> subscribeToSessionChannel() {
        final Promise<Object> promise = new Promise<Object>();
        try {
            SubscribeArgs args = new SubscribeArgs(sessionChannel){{
                setOnSuccess(new fm.SingleAction<SubscribeSuccessArgs>() {
                    public void invoke(PublishSuccessArgs e) {
                        promise.resolve(null);
                    }
                });
                setOnFailure(new fm.SingleAction<SubscribeFailureArgs>() {
                    public void invoke(SubscribeFailureArgs e) {
                        promise.reject(e.getException());
                    }
                });
            }};
            fm.websync.subscribers.SubscribeArgsExtensions.setOnClientSubscribe(args, new SingleAction<ClientSubscribeArgs>(){
                public void invoke(ClientSubscribeArgs e) {
                    String remoteClientId = e.getClient().getClientId().toString();
                    final String remoteUserId = Serializer.deserializeString(e.getSubscribedClient().getBoundRecords().get(userIdKey).getValueJson());

                    final Connection connection = createConnectionAndWireOnLocalCandidate(new PeerClient(e.getSubscribedClient().getClientId().toString(), e.getSubscribedClient().getBoundRecords()), remoteUserId);
                    connection.setExternalId(remoteClientId);
                    connections.add(connection);

                    connection.createOffer().then(new IFunction1<SessionDescription, Future<SessionDescription>>() {
                        @Override
                        public Future<SessionDescription> invoke(SessionDescription offer) {
                            return connection.setLocalDescription(offer);
                        }
                    }).then(new IAction1<SessionDescription>() {
                        @Override
                        public void invoke(SessionDescription offer) {
                            try {
                                client.publish(new PublishArgs(remoteUserChannel(remoteUserId), offer.toJson(), offerTag){{
                                    setOnSuccess(new fm.SingleAction<PublishSuccessArgs>() {
                                        public void invoke(PublishSuccessArgs publishSuccessArgs) {
                                            Log.info("Published offer to remote peer.");
                                        }
                                    });
                                    setOnFailure(new fm.SingleAction<PublishFailureArgs>() {
                                        public void invoke(PublishFailureArgs publishFailureArgs) {
                                            Log.error("Could not publish offer to remote peer.", publishFailureArgs.getException());
                                        }
                                    });
                                }});
                            }
                            catch (Exception ex) {
                                Log.error(ex.getMessage());
                            }
                        }
                    });
                }
            });

            fm.websync.subscribers.SubscribeArgsExtensions.setOnClientUnsubscribe(args, new SingleAction<ClientUnsubscribeArgs>(){
                public void invoke(ClientUnsubscribeArgs e) {
                    String remoteClientId = e.getClient().getClientId().toString();

                    Connection connection = connections.getById(remoteClientId);
                    if (connection != null) {
                        connections.remove(connection);
                        connection.close();
                    }
                }
            });
            client.subscribe(args);
        }
        catch (Exception ex){
            Log.error("Could not subscribe to session channel.");
        }
        return promise;
    }

    private Connection createConnectionAndWireOnLocalCandidate(PeerClient remoteClient, final String remoteUserId) {
        final Connection connection = createConnection.invoke(remoteClient);
        connection.addOnLocalCandidate(new IAction2<Connection, Candidate>() {
            @Override
            public void invoke(Connection conn, Candidate candidate) {
                try {
                    client.publish(new PublishArgs(remoteUserChannel(remoteUserId), candidate.toJson(), candidateTag){{
                        setOnSuccess(new SingleAction<PublishSuccessArgs>(){
                            public void invoke(PublishSuccessArgs publishSuccessArgs) {
                                Log.info("Published candidate to remote peer.");
                            }
                        });
                        setOnFailure(new SingleAction<PublishFailureArgs>(){
                            public void invoke(PublishFailureArgs publishSuccessArgs) {
                                Log.error("Could not publish candidate to remote peer.", publishSuccessArgs.getException());
                            }
                        });
                    }});
                }
                catch (Exception ex) {
                    Log.error("Could not publish candidate to remote peer.");
                }
            }
        });

        return connection;
    }

    public void reconnect(PeerClient remoteClient, Connection connection) {
        // TODO
    }
}
