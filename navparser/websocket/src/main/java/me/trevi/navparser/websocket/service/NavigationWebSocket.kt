package me.trevi.navparser.websocket.service

import android.content.Intent
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import me.trevi.navparser.BuildConfig
import me.trevi.navparser.lib.NavigationData
import me.trevi.navparser.lib.NavigationNotification
import me.trevi.navparser.service.NavigationListener
import me.trevi.navparser.websocket.*
import me.trevi.navparser.websocket.proto.*
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.starProjectedType
import timber.log.Timber as Log

val SOCKET_PORTS: IntArray = intArrayOf(35456, 12315, 57535)

open class NavigationWebSocket : NavigationListener() {
    lateinit var mServer : NettyApplicationEngine
    private var mPrevNavData = NavigationData()
    private var mConsumer : DefaultWebSocketSession? = null /* FIXME: Support multiple consumers */
    private var mSocketPorts = SOCKET_PORTS

    protected var socketPorts : IntArray
        get() = mSocketPorts
        set (value) {
            mSocketPorts = value
            if (mSocketPorts.isEmpty())
                mSocketPorts = SOCKET_PORTS
            else {
                if (this::mServer.isInitialized)
                    mServer.stop(0, 0, TimeUnit.MILLISECONDS)
                tryRunServer()
            }
        }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG)
            Log.plant(Log.DebugTree())

        tryRunServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (this::mServer.isInitialized && !mServer.application.isActive)
            mServer.start()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        mServer.stop(0, 0, TimeUnit.MILLISECONDS)
    }

    protected open fun handleRequest(req: NavProtoEvent) : NavProtoEvent? {
        Log.d("Handling request $req")

        when (req.action) {
            NavProtoAction.resync -> {
                if (currentNotification != null) {
                    sendNavigationData(currentNotification!!.navigationData, forceComplete = true)
                } else {
                    if (!haveNotificationsAccess()) {
                        return NavProtoEvent.newError(
                            NavProtoErrorKind.no_notifications_access,
                            "The service has no notifications access"
                        )
                    }
                    return NavProtoEvent.newError(NavProtoErrorKind.no_navigation_in_progres,
                        "No navigation in progress")
                }
            }
            NavProtoAction.set -> {
                (req.data as NavProtoNavigationOptions).also {
                    notificationsThreshold = it.notificationThreshold
                    return NavProtoEvent(NavProtoAction.status, NavProtoMessage("ok"))
                }
            }
            NavProtoAction.get -> {
                return NavProtoEvent(NavProtoAction.get,
                    NavProtoNavigationOptions(notificationsThreshold))
            }
            else -> return NavProtoEvent.newError(NavProtoErrorKind.invalid_action,
                "Impossible to handle action ${req.action}")
        }
        return null
    }

    private suspend fun handleTextFrame(frame : Frame.Text) {
        try {
            NavProtoEvent.fromTextFrame(frame).also { req ->
                handleRequest(req).also { reply ->
                    if (reply != null) {
                        if (reply.action == NavProtoAction.error) {
                            Log.e("Error handling client request '$req': ${reply.data}")
                        }
                        sendNavigationEventSuspended(reply)
                    }
                }
            }
        } catch (e: Throwable) {
            sendNavigationEventSuspended(NavProtoEvent.newError(NavProtoErrorKind.invalid_request,
                "Failed to handle client request ${frame.readText()}: $e"))
        }
    }

    private fun setupServer(port: Int) {
        Log.i("Creating WebSocket server on port $port")

        mServer = embeddedServer(Netty, port) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(60)
                timeout = Duration.ofSeconds(15)
                maxFrameSize = Long.MAX_VALUE
            }
            install(Routing) {
                webSocket("/navigationSocket") {
                    if (mConsumer != null && mConsumer!!.isActive) {
                        sendNavigationEventSuspended(this,  NavProtoEvent.newError(
                            NavProtoErrorKind.not_authorized,
                            "We've already a client... Refusing new connection"))
                        close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT,
                            "Another Client is already connected"))
                        return@webSocket
                    }

                    Log.d("Connection with peer $this started")

                    try {
                        enabled = true
                        mPrevNavData = NavigationData()
                        mConsumer = this

                        sendNavigationEventSuspended(
                            NavProtoEvent(
                                NavProtoAction.hello,
                                NavProtoHello("Hello, ready to navigate you!")
                            )
                        )

                        if (!haveNotificationsAccess()) {
                            sendNavigationEventSuspended(NavProtoEvent.newError(
                                NavProtoErrorKind.no_notifications_access,
                                "The service has no notifications access"
                            ))
                        }

                        incoming.consumeEach { frame ->
                            Log.d("Got incoming frame $frame")
                            when (frame) {
                                is Frame.Text -> {
                                    handleTextFrame(frame)
                                }
                                is Frame.Close -> {
                                    mConsumer = null
                                    return@consumeEach
                                }
                                else -> {
                                    sendNavigationEventSuspended(NavProtoEvent.newError(
                                        NavProtoErrorKind.invalid_request,
                                        "Frame $frame not handled"))
                                }
                            }
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        Log.w("Connection closed: ${closeReason.await()}")
                    } catch (e: Throwable) {
                        Log.e("Connection error: ${closeReason.await()}")
                        e.printStackTrace()
                    } finally {
                        Log.d("Connection with peer $mConsumer closed")
                        mConsumer = null
                        enabled = false
                    }
                }
            }
        }

        mServer.start()
    }

    private fun tryRunServer() {
        socketPorts.forEach {
            try {
                Log.d("Setting up server?! $it")
                setupServer(it)
                return@tryRunServer
            } catch (e: Exception) {
                Log.w("Impossible to create a WebSocket on port $it: $e")
            }
        }

        Log.e("No available port found, impossible to run ${applicationContext.packageName}")
    }

    override fun onNavigationNotificationAdded(navNotification: NavigationNotification) {
        super.onNavigationNotificationAdded(navNotification)

        onNavigationNotificationUpdated(navNotification)
    }

    private fun NavigationData.jsonDiffSerializable(other: NavigationData) : Map<String, JsonElement> {
        val jsonEncoder = Json{ encodeDefaults = true }
        val diff = emptyMap<String, JsonElement>().toMutableMap()

        diffMap(other).forEach {
            diff[it.key] = if (it.value != null)
                jsonEncoder.encodeToJsonElement(serializer(it.value!!::class.starProjectedType), it.value)
            else JsonNull
        }

        return diff
    }

    private suspend fun sendNavigationEventSuspended(client : DefaultWebSocketSession?,
                                                     navigationEvent : NavProtoEvent) : Boolean {
        if (navigationEvent.data is NavProtoError) {
            Log.e("${navigationEvent.data.error}: ${navigationEvent.data.message}")
        } else {
            Log.v("Sending to $client: $navigationEvent")
        }

        if (client != null && client.isActive) {
            client.send(navigationEvent.toTextFrame())
            return true
        }

        return false
    }

    protected suspend fun sendNavigationEventSuspended(navigationEvent : NavProtoEvent) {
        if (!sendNavigationEventSuspended(mConsumer, navigationEvent)) {
            enabled = false
            mConsumer = null
        }
    }

    protected fun sendNavigationEvent(navigationEvent : NavProtoEvent) {
        GlobalScope.launch(Dispatchers.Main) { sendNavigationEventSuspended (navigationEvent) }
    }

    protected fun sendNavigationData(navigationData: NavigationData, forceComplete: Boolean = false) {
        if (forceComplete || !mPrevNavData.isValid()) {
            sendNavigationEvent(
                NavProtoEvent(
                    NavProtoAction.status,
                    NavProtoNavigation(navigationData))
            )
            mPrevNavData = navigationData
        } else {
            GlobalScope.launch(Dispatchers.Main) {
                GlobalScope.async(Dispatchers.Default) {
                    val diff = mPrevNavData.jsonDiffSerializable(navigationData)
                    return@async if (diff.isNotEmpty()) Json.encodeToJsonElement(diff) else null
                }.await().also {
                    if (it != null) {
                        sendNavigationEventSuspended(
                            NavProtoEvent(
                                NavProtoAction.status,
                                NavProtoNavigationUpdate(it)))
                        mPrevNavData = navigationData
                    }
                }
            }
        }
    }

    override fun onNavigationNotificationUpdated(navNotification: NavigationNotification) {
        if (mConsumer == null)
            return

        Log.v("Sending ${navNotification.navigationData}")

        sendNavigationData(navNotification.navigationData)
    }
}
