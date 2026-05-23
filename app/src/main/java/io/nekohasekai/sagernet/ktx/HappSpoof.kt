package io.nekohasekai.sagernet.ktx

import android.os.Build
import io.nekohasekai.sagernet.database.SubscriptionBean
import libsagernetcore.HTTPRequest
import java.security.SecureRandom

object HappSpoof {

    private const val FALLBACK_DEVICE_MODEL = "Pixel 7 Pro"
    private const val FALLBACK_OS_VERSION = "16"

    private val rng = SecureRandom()

    fun defaultDeviceModel(): String {
        return Build.MODEL.takeIf { it.isNotBlank() } ?: FALLBACK_DEVICE_MODEL
    }

    fun defaultOsVersion(): String {
        return Build.VERSION.RELEASE.takeIf { it.isNotBlank() } ?: FALLBACK_OS_VERSION
    }

    private fun String?.orDefaultDeviceModel(): String {
        val value = this?.trim().orEmpty()
        return value.ifEmpty { defaultDeviceModel() }
    }

    private fun String?.orDefaultOsVersion(): String {
        val value = this?.trim().orEmpty()
        return value.ifEmpty { defaultOsVersion() }
    }

    fun randomUserId(): String {
        val sb = StringBuilder(20)
        sb.append(rng.nextInt(9) + 1)
        repeat(19) { sb.append(rng.nextInt(10)) }
        return sb.toString()
    }

    fun randomHwid(): String {
        val bytes = ByteArray(8)
        rng.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun ensureIds(sub: SubscriptionBean): Boolean {
        var changed = false
        if (sub.happUserId.isNullOrEmpty()) {
            sub.happUserId = randomUserId()
            changed = true
        }
        if (sub.happHwid.isNullOrEmpty()) {
            sub.happHwid = randomHwid()
            changed = true
        }
        return changed
    }

    fun apply(request: HTTPRequest, sub: SubscriptionBean) {
        ensureIds(sub)
        val appVersion = sub.happAppVersion.ifEmpty { "3.21.1" }
        val os = sub.happOs.ifEmpty { "Android" }
        val osVersion = sub.happOsVersion.orDefaultOsVersion()
        val deviceModel = sub.happDeviceModel.orDefaultDeviceModel()
        val locale = sub.happLocale.ifEmpty { "en" }
        request.setUserAgent("Happ/$appVersion/$os/${sub.happUserId}")
        request.setHeader("x-device-locale", locale)
        request.setHeader("x-hwid", sub.happHwid)
        request.setHeader("x-device-os", os)
        request.setHeader("x-ver-os", osVersion)
        request.setHeader("x-device-model", deviceModel)
        request.setHeader("accept-encoding", "gzip")
    }
}
