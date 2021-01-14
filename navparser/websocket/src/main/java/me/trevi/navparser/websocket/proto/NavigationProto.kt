package me.trevi.navparser.websocket.proto

import io.ktor.http.cio.websocket.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import me.trevi.navparser.lib.*

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
    not_supported,
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
@SerialName("navigationStart")
data class NavProtoNavigationStart(
    val destination: String,
    val mode: NavigationMode = NavigationMode.DRIVING,
    val avoid: NavigationAvoid = emptySet(),
) : NavigationProtoActionType()

@Serializable
@SerialName("navigationState")
data class NavProtoNavigation(
    val navigationData: NavigationData
) : NavigationProtoActionType()

typealias UntypedNavigationDataDiff = MapStringAnySerializableOnly
@Serializable
@SerialName("navigationUpdate")
data class NavProtoNavigationUpdate(
    val navigationData: UntypedNavigationDataDiff
) : NavigationProtoActionType()

@Serializable
@SerialName("options")
data class NavProtoNavigationOptions(
    val notificationThreshold : Long = 0,
    val useBinary : Boolean = false,
) : NavigationProtoActionType()

@Serializable
data class NavProtoEvent(
    val action: NavProtoAction,
    val data: NavigationProtoActionType = NavProtoNullData,
) {
    fun toTextFrame() : Frame.Text {
        return Frame.Text(Json{ encodeDefaults = encodeDefaults() }.encodeToString(this))
    }

    fun toBinaryFrame() : Frame.Binary {
        return Frame.Binary(true, Cbor{ encodeDefaults = encodeDefaults() }.encodeToByteArray(this))
    }

    private fun encodeDefaults() : Boolean {
        return when (data) {
            is NavProtoNavigationUpdate -> true
            else -> false
        }
    }

    companion object {
        fun newError(error: NavProtoErrorKind, message: String = "") : NavProtoEvent {
            return NavProtoEvent(NavProtoAction.error, NavProtoError(error, message))
        }

        fun fromTextFrame(frame: Frame.Text) : NavProtoEvent {
            return Json.decodeFromString(frame.readText())
        }

        fun fromBinaryFrame(frame: Frame.Binary) : NavProtoEvent {
            return Cbor.decodeFromByteArray(frame.readBytes())
        }
    }
}
