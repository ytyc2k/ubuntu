/*
 * Copyright (c) 2019 Umbrela Smart Inc.
 */

package umbrela.co.umbrelacam

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import fm.icelink.*
import fm.icelink.android.LayoutManager
import umbrela.co.umbrelacam.icelink.AecContext
import umbrela.co.umbrelacam.icelink.CameraLocalMedia
import umbrela.co.umbrelacam.icelink.LocalMedia
import umbrela.co.umbrelacam.icelink.RemoteMedia
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class IceClient(
        private val context: Context,
        val localAudioEnabled: Boolean,
        val localVideoEnabled: Boolean,
        val remoteAudioEnabled: Boolean,
        val remoteVideoEnabled: Boolean,
        videoContainer: FrameLayout,
        private val onConnect: (remoteClientId: String, firstIsConnected: Boolean) -> Unit,
        private val onDisconnect: (remoteClientId: String, lastIsDisconnected: Boolean) -> Unit,
        private val logger: Logger,
        private val logging: Boolean = false
) : Closeable {

    companion object {
        private const val DEFAULT_TIMEOUT = 10000 // timeout for promises in millis
        private const val ICE_DEFAULT_USERNAME = "umbrela"
        private const val ICE_DEFAULT_PASSWORD = "umbrela"
        //private const val XIRSYS_DEMO_IDENT = "umbrela"
        //private const val XIRSYS_DEMO_SECRET = "19791e00-3ade-11e9-91a4-5978b2c8072d"
        //private const val XIRSYS_DEMO_CHANNEL = "Umbrela"
    }

    //  IceLink
    private val enableH264 = false
    private var aecContext: AecContext? = null
    private var layoutManager: LayoutManager? = null
    private var localMedia: LocalMedia<*>? = null
    private val remoteMediaList: MutableList<RemoteMedia> = mutableListOf()
    private val connections: ConcurrentMap<String, Connection> = ConcurrentHashMap()

    init {
        if (logging) {
            Log.setLogLevel(LogLevel.Verbose)
            Log.registerProvider(fm.icelink.android.LogProvider(LogLevel.Verbose))
        }

        loadIceLinkLicenseKey(context)

        if (!Global.equals(Platform.getInstance().architecture, Architecture.X86)) {
            aecContext = AecContext()
            logger.verbose("aecContext is created")
        }

        layoutManager = LayoutManager(videoContainer)
        logger.verbose("layoutManager is created")

        if (localAudioEnabled || localVideoEnabled) {
            localMedia = CameraLocalMedia(context, enableH264, !localAudioEnabled, !localVideoEnabled, aecContext)
            logger.verbose("localMedia is created")
            layoutManager?.localView = localMedia?.view as View
            logger.verbose("local view is set")
        }
    }

    @Synchronized
    override fun close() {
        if (logging) {
            Log.unregisterProviders()
        }

        layoutManager?.removeRemoteViews()
        logger.verbose("remote views are removed")

        if (localAudioEnabled || localVideoEnabled) {
            layoutManager?.unsetLocalView()
            logger.verbose("local view is unset")
        }

        layoutManager = null
        logger.verbose("layoutManager is destroyed")

        if (localAudioEnabled || localVideoEnabled) {
            localMedia?.destroy()
            localMedia = null
            logger.verbose("localMedia is destroyed")
        }

        aecContext?.let {
            it.destroy()
            aecContext = null
            logger.verbose("aecContext is destroyed")
        }
    }

    var isLocalAudioMuted: Boolean
        get() = localMedia?.audioMuted == true
        set(muted) {
            localMedia?.audioMuted = muted
        }

    var isLocalVideoMuted: Boolean
        get() = localMedia?.videoMuted == true
        set(muted) {
            localMedia?.videoMuted = muted
            layoutManager?.localView?.alpha = if (muted) 0f else 1f
        }

    var isRemoteAudioMuted: Boolean
        get() = remoteMediaList.all { it.audioMuted }
        set(muted) {
            remoteMediaList.forEach { it.audioMuted = muted }
        }

    var isRemoteVideoMuted: Boolean
        get() = remoteMediaList.all { it.videoMuted }
        set(muted) {
            remoteMediaList.forEach { it.videoMuted = muted }
        }

    val isConnected: Boolean
        @Synchronized
        get() = connections.isNotEmpty()

    @Synchronized
    fun isConnected(remoteClientId: String): Boolean {
        return connections.containsKey(remoteClientId)
    }

    @Synchronized
    fun connectOnOffer(
            remoteClientId: String,
            iceHost: String?,
            onOffer: (offerJson: String) -> Unit,
            onCandidate: (candidateJson: String) -> Unit
    ) {
        val connection = connect(remoteClientId, iceHost)
        val offer = connection.createOffer().waitForResult()
        connection.addOnLocalCandidate { c: fm.icelink.Connection, candidate: fm.icelink.Candidate ->
            onCandidate(candidate.toJson())
        }
        offer.tieBreaker = UUID.randomUUID().toString()
        connection.localDescription = offer
        onOffer(offer.toJson())
    }

    @Synchronized
    fun connectOnAnswer(
            offerJson: String,
            remoteClientId: String,
            iceHost: String?,
            onAnswer: (answerJson: String) -> Unit
    ) {
        val connection = connect(remoteClientId, iceHost)
        val offer = SessionDescription.fromJson(offerJson)
        connection.setRemoteDescription(offer).waitForResult()
        val answer = connection.createAnswer().waitForResult()
        answer.tieBreaker = UUID.randomUUID().toString()
        connection.localDescription = answer
        onAnswer(answer.toJson())
    }

    @Synchronized
    private fun connect(remoteClientId: String, iceHost: String?): Connection {
        val remoteMedia = RemoteMedia(
                remoteClientId,
                context,
                enableH264,
                !remoteAudioEnabled,
                !remoteVideoEnabled,
                aecContext)

        remoteMediaList.add(remoteMedia)
        logger.verbose("remoteMedia is created for $remoteClientId")

        val streams = mutableListOf<Stream>()
        if (localAudioEnabled || remoteAudioEnabled) {
            val audioStream = AudioStream(if (localAudioEnabled) localMedia else null, if (remoteAudioEnabled) remoteMedia else null)
            streams.add(audioStream)
        }
        if (localVideoEnabled || remoteVideoEnabled) {
            val videoStream = VideoStream(if (localVideoEnabled) localMedia else null, if (remoteVideoEnabled) remoteMedia else null)
            streams.add(videoStream)
        }

        val connection = Connection(streams.toTypedArray())
        //connection.multiplexPolicy = fm.icelink.MultiplexPolicy.Negotiated
        connection.trickleIcePolicy = fm.icelink.TrickleIcePolicy.NotSupported
        connection.iceServers = generateIceServers(iceHost)

        connections[remoteClientId] = connection
        logger.debug("$remoteClientId is connected")

        layoutManager?.addRemoteView(remoteClientId, remoteMedia.view)
        logger.verbose("remoteView is added for $remoteClientId")

        val weakThis = WeakReference(this)
        connection.addOnStateChange { c ->
            if (c.state == ConnectionState.Closing || c.state == ConnectionState.Failing || c.state == ConnectionState.Closed || c.state == ConnectionState.Failed) {
                weakThis.get()?.disconnect(remoteClientId)
            }
        }

        onConnect(remoteClientId, connections.count() == 1)

        return connection
    }

    @Synchronized
    fun setAnswer(answerJson: String, remoteClientId: String) {
        val answer = SessionDescription.fromJson(answerJson)
        val connection = connections[remoteClientId]
        connection?.remoteDescription = answer
    }

    @Synchronized
    fun addCandidate(candidateJson: String, remoteClientId: String) {
        val candidate = Candidate.fromJson(candidateJson)
        val connection = connections[remoteClientId]
        connection?.addRemoteCandidate(candidate)
    }

    @Synchronized
    fun disconnect(remoteClientId: String): Boolean {
        val connection = connections.remove(remoteClientId) ?: return false
        connection.close()
        logger.debug("$remoteClientId is disconnected")

        layoutManager?.removeRemoteView(remoteClientId)
        logger.verbose("remoteView is removed for $remoteClientId")

        remoteMediaList.find {
            it.id == remoteClientId
        }?.let { remoteMedia ->
            remoteMedia.changeAudioSinkOutput(null).waitForPromise(DEFAULT_TIMEOUT)
            remoteMedia.changeVideoSinkOutput(null).waitForPromise(DEFAULT_TIMEOUT)
            remoteMedia.destroy()
            remoteMediaList.remove(remoteMedia)
            logger.verbose("remoteMedia is destroyed for $remoteClientId")
        }

        onDisconnect(remoteClientId, connections.isEmpty())

        return true
    }

    @Synchronized
    fun disconnectAll() {
        connections.keys.forEach { remoteClientId ->
            disconnect(remoteClientId)
        }
    }

    /**
     * Method used to validate IceLink's license key.
     */
    private fun loadIceLinkLicenseKey(context: Context) {
        try {
            val `is` = context.resources.openRawResource(R.raw.icelink)
            val reader = BufferedReader(InputStreamReader(`is`))
            val total = StringBuilder()
            do {
                val line = reader.readLine()
                line?.let { total.append(it).append('\n') }
            } while (line != null)
            try {
                fm.icelink.License.setKey(total.toString())
                logger.verbose("Icelink license is loaded.")
            } catch (e: RuntimeException) {
                logger.error("Error loading IceLink license key: ${e.message}")
            }

            `is`.close()
        } catch (e: IOException) {
            logger.error("Invalid IceLink key")
        }

    }

    @Synchronized
    fun startLocalMedia() {
        val localMedia = localMedia ?: return
        localMedia.start()
                .then { _ -> logger.verbose("startLocalMedia: success") }
                .fail(IAction1 { e: Exception -> logger.error("startLocalMedia: ${e.message}") })
                .waitForPromise(DEFAULT_TIMEOUT)
    }

    @Synchronized
    fun stopLocalMedia() {
        val localMedia = localMedia ?: return
        localMedia.stop()
                .then { _ -> logger.verbose("stopLocalMedia: success") }
                .fail(IAction1 { e: Exception -> logger.error("stopLocalMedia: ${e.message}") })
                .waitForPromise(DEFAULT_TIMEOUT)
    }

    @Synchronized
    fun stopAndDestroyRemoteMediaListIfNeeded() {
        if (remoteMediaList.isEmpty()) return
        for (remoteMedia in remoteMediaList) {
            remoteMedia.changeAudioSinkOutput(null).waitForPromise(DEFAULT_TIMEOUT)
            remoteMedia.changeVideoSinkOutput(null).waitForPromise(DEFAULT_TIMEOUT)
            remoteMedia.destroy()
        }
        remoteMediaList.clear()
        logger.warn("remoteMediaList is destroyed")
    }

    private fun generateIceServers(iceHost: String?): Array<IceServer> {
        return if (iceHost != null) {
            arrayOf(
                    IceServer("$iceHost:3478"),
                    IceServer(
                            "$iceHost:3478",
                            ICE_DEFAULT_USERNAME,
                            ICE_DEFAULT_PASSWORD
                    )
            )
        } else {
            arrayOf(
                    IceServer("stun:u3.xirsys.com"),
                    IceServer(
                            "turn:u3.xirsys.com:80?transport=udp",
                            "79f84fe2-594f-11e8-b79e-2eb2709e7085",
                            "79f85122-594f-11e8-9103-93c524e812b2"
                    ),
                    IceServer(
                            "turn:u3.xirsys.com:3478?transport=udp",
                            "79f84fe2-594f-11e8-b79e-2eb2709e7085",
                            "79f85122-594f-11e8-9103-93c524e812b2"
                    ),
                    IceServer(
                            "turn:u3.xirsys.com:80?transport=tcp",
                            "79f84fe2-594f-11e8-b79e-2eb2709e7085",
                            "79f85122-594f-11e8-9103-93c524e812b2"
                    ),
                    IceServer(
                            "turn:u3.xirsys.com:3478?transport=tcp",
                            "79f84fe2-594f-11e8-b79e-2eb2709e7085",
                            "79f85122-594f-11e8-9103-93c524e812b2"
                    ),
                    IceServer(
                            "turns:u3.xirsys.com:443?transport=tcp",
                            "79f84fe2-594f-11e8-b79e-2eb2709e7085",
                            "79f85122-594f-11e8-9103-93c524e812b2"
                    ),
                    IceServer(
                            "turns:u3.xirsys.com:5349?transport=tcp",
                            "79f84fe2-594f-11e8-b79e-2eb2709e7085",
                            "79f85122-594f-11e8-9103-93c524e812b2"
                    )
            )
        }
    }

}