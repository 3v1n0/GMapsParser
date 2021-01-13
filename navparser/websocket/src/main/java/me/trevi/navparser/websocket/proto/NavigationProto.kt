package me.trevi.navparser.websocket.proto

import io.ktor.http.cio.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.trevi.navparser.lib.NavigationData
import me.trevi.navparser.lib.NavigationDataMap

const val PROTO_VERSION = "1.0"

enum class NavProtoAction {
    hello,
    status,
    error,
    resync,
    set,
    get,
    start,
    stop,
    none,
}

enum class NavProtoErrorKind {
    not_authorized,
    no_navigation_in_progres,
    no_notifications_access,
    invalid_request,
    invalid_action,
    internal,
}

@Serializable
sealed class NavigationProtoActionType

@Serializable
@SerialName("none")
object NavProtoNullData : NavigationProtoActionType()

@Serializable
@SerialName("hello")
data class NavProtoHello(
    val message: String,
) : NavigationProtoActionType()
{
    var version: String = ""

    init {
        version = PROTO_VERSION
    }
}

@Serializable
@SerialName("message")
data class NavProtoMessage(
    val message: String
) : NavigationProtoActionType()

@Serializable
@SerialName("error")
data class NavProtoError(
    val error : NavProtoErrorKind,
    val message: String = ""
) : NavigationProtoActionType()

@Serializable
@SerialName("navigationState")
data class NavProtoNavigation(
    val navigationData: NavigationData
) : NavigationProtoActionType()

@Serializable
@SerialName("navigationUpdate")
data class NavProtoNavigationUpdate(
    val navigationData: NavigationDataMap
) : NavigationProtoActionType()

@Serializable
@SerialName("options")
data class NavProtoNavigationOptions(
    val notificationThreshold : Long
) : NavigationProtoActionType()

@Serializable
data class NavProtoEvent(
    val action: NavProtoAction,
    val data: NavigationProtoActionType = NavProtoNullData,
) {
    fun toTextFrame() : Frame.Text {
        return Frame.Text(Json.encodeToString(this))
    }

    companion object {
        fun newError(error: NavProtoErrorKind, message: String = "") : NavProtoEvent {
            return NavProtoEvent(NavProtoAction.error, NavProtoError(error, message))
        }

        fun fromTextFrame(frame: Frame.Text) : NavProtoEvent {
            return Json.decodeFromString(frame.readText())
        }
    }
}
