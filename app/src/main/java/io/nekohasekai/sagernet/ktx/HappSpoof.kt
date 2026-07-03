package io.nekohasekai.sagernet.ktx

import android.os.Build
import io.nekohasekai.sagernet.database.SubscriptionBean
import libexclavecore.HTTPRequest
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

    data class Device(val os: String, val osVersion: String, val model: String)

    private const val MAX_ANDROID = 16

    // generated pool of real device families; every entry keeps platform,
    // OS version and model coherent
    private val DEVICES: List<Device> by lazy {
        buildList {
            fun android(model: String, launch: Int, support: Int = 3) {
                val from = launch.coerceIn(7, MAX_ANDROID)
                val to = (launch + support).coerceIn(from, MAX_ANDROID)
                for (os in from..to) add(Device("Android", os.toString(), model))
            }

            // Google Pixel 1-10 (a/XL/Pro variants where they exist)
            for (gen in 1..10) {
                val launch = (gen + 6).coerceAtMost(MAX_ANDROID)
                val support = if (gen >= 8) 7 else 3 // Pixel 8+ has 7 years of updates
                android("Pixel $gen", launch, support)
                if (gen <= 4) android("Pixel $gen XL", launch, support)
                if (gen >= 6) android("Pixel $gen Pro", launch, support)
                if (gen in 3..9) android("Pixel ${gen}a", launch, support)
            }

            // Samsung Galaxy S7-S10, S20-S26 (base/+/Ultra, real SM- model codes)
            data class SGen(val launch: Int, val base: String, val plus: String?, val ultra: String?)
            listOf(
                SGen(6, "SM-G930F", "SM-G935F", null),   // S7 / S7 edge
                SGen(7, "SM-G950F", "SM-G955F", null),   // S8 / S8+
                SGen(8, "SM-G960F", "SM-G965F", null),   // S9 / S9+
                SGen(9, "SM-G973F", "SM-G975F", null),   // S10 / S10+
                SGen(10, "SM-G980F", "SM-G985F", "SM-G988B"), // S20
                SGen(11, "SM-G991B", "SM-G996B", "SM-G998B"), // S21
                SGen(12, "SM-S901B", "SM-S906B", "SM-S908B"), // S22
                SGen(13, "SM-S911B", "SM-S916B", "SM-S918B"), // S23
                SGen(14, "SM-S921B", "SM-S926B", "SM-S928B"), // S24
                SGen(15, "SM-S931B", "SM-S936B", "SM-S938B"), // S25
                SGen(16, "SM-S941B", "SM-S946B", "SM-S948B"), // S26
            ).forEach { g ->
                listOfNotNull(g.base, g.plus, g.ultra).forEach { android(it, g.launch, 4) }
            }

            // Xiaomi Redmi Note 4-15 (base/Pro)
            for (gen in 4..15) {
                val launch = (gen + 1).coerceIn(7, MAX_ANDROID)
                android("Redmi Note $gen", launch)
                android("Redmi Note $gen Pro", launch)
            }

            // OnePlus, OPPO, realme, vivo, Huawei/Honor mid pool
            listOf(
                "ONEPLUS A6013" to 9, "OnePlus 8 Pro" to 10, "OnePlus 9" to 11,
                "CPH2423" to 12, "CPH2451" to 13, "CPH2581" to 14, "CPH2611" to 14,
                "RMX3563" to 12, "RMX3771" to 13, "RMX3851" to 14,
                "V2254A" to 13, "V2324A" to 14,
                "M2101K6G" to 11, "2201117TG" to 12, "23021RAA2Y" to 13, "2404APC5FG" to 14,
            ).forEach { (model, launch) -> android(model, launch) }

            // iPhone 8 .. iPhone 16 line (real identifiers), iOS 11-18
            listOf(
                "iPhone10,1" to 11, "iPhone10,3" to 11, // 8 / X
                "iPhone11,2" to 12, "iPhone11,8" to 12, // XS / XR
                "iPhone12,1" to 13, "iPhone12,3" to 13, // 11 / 11 Pro
                "iPhone13,2" to 14, "iPhone13,4" to 14, // 12 / 12 Pro Max
                "iPhone14,5" to 15, "iPhone14,2" to 15, // 13 / 13 Pro
                "iPhone14,7" to 16, "iPhone15,3" to 16, // 14 / 14 Pro Max
                "iPhone15,4" to 17, "iPhone16,1" to 17, // 15 / 15 Pro
                "iPhone17,3" to 18, "iPhone17,2" to 18, // 16 / 16 Pro
            ).forEach { (model, launch) ->
                for (os in launch..18) add(Device("iOS", "$os", model))
            }
        }
    }

    private val APP_VERSIONS = listOf("3.21.1", "3.21.0", "3.20.4", "3.20.2", "3.19.1")

    private val LOCALES = listOf("en", "ru", "uk", "tr", "de", "es", "pt", "fa", "id", "vi")

    fun randomDevice(): Device {
        val device = DEVICES[rng.nextInt(DEVICES.size)]
        return if (device.os == "iOS") {
            // iOS versions are reported with a minor component, e.g. 17.6
            device.copy(osVersion = "${device.osVersion}.${rng.nextInt(7)}")
        } else {
            device
        }
    }

    fun randomAppVersion(): String = APP_VERSIONS[rng.nextInt(APP_VERSIONS.size)]

    fun randomLocale(): String = LOCALES[rng.nextInt(LOCALES.size)]

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
