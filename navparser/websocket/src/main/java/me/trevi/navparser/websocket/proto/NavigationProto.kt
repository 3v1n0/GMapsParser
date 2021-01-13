package me.trevi.navparser.websocket.proto

import io.ktor.http.cio.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.trevi.navparser.lib.NavigationData

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
@SerialName("navigationState")
data class NavProtoNavigation(
    val navigationData: NavigationData
) : NavigationProtoActionType()

@Serializable
@SerialName("navigationUpdate")
data class NavProtoNavigationUpdate(
    val navigationData: JsonElement
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
        fun newError(message: String) : NavProtoEvent {
            return NavProtoEvent(NavProtoAction.error, NavProtoMessage(message))
        }

        fun fromTextFrame(frame: Frame.Text) : NavProtoEvent {
            return Json.decodeFromString(frame.readText())
        }
    }
}
