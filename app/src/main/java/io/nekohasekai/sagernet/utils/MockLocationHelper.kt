package io.nekohasekai.sagernet.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.widget.Toast
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import java.util.concurrent.atomic.AtomicInteger

object MockLocationHelper {

    private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private const val OFFSET_METERS = 2000.0
    private const val LOCATION_REFRESH_INTERVAL = 10_000L
    private val syncGeneration = AtomicInteger()
    private var syncJob: Job? = null

    fun isMockLocationAppAllowed(context: Context = SagerNet.application): Boolean {
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            @Suppress("DEPRECATION")
            return Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION,
            ) != "0"
        }
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        if (appOps != null) {
            @Suppress("DEPRECATION")
            if (appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    pkg,
                ) == AppOpsManager.MODE_ALLOWED
            ) {
                return true
            }
        }
        @Suppress("DEPRECATION")
        return Settings.Secure.getString(context.contentResolver, "mock_location") == pkg
    }

    fun openDeveloperSettings(context: Context) {
        startSettingsIntent(
            context,
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    fun openLocationSettings(context: Context) {
        startSettingsIntent(
            context,
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            Intent("android.settings.LOCATION_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun startSettingsIntent(context: Context, vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Throwable) {
            }
        }
    }

    fun disableMockGeoSetting() {
        DataStore.mockGeoLocation = false
        stop()
    }

    fun randomNearby(latitude: Double, longitude: Double, radiusMeters: Double = OFFSET_METERS): Pair<Double, Double> {
        val angle = Random.nextDouble(0.0, kotlin.math.PI * 2)
        val distance = sqrt(Random.nextDouble()) * radiusMeters
        val metersPerDegreeLat = 111_320.0
        val dLat = distance * cos(angle) / metersPerDegreeLat
        val dLon = distance * sin(angle) / (metersPerDegreeLat * cos(Math.toRadians(latitude)))
        return latitude + dLat to longitude + dLon
    }

    fun syncForCurrentProfile() {
        if (!DataStore.mockGeoLocation) {
            stop()
            return
        }
        if (!SagerNet.started) {
            stop()
            return
        }
        if (!isMockLocationAppAllowed()) {
            Logs.w("Mock location: app is not selected as mock location provider")
            stop()
            return
        }
        val profile = SagerDatabase.proxyDao.getById(DataStore.startedProfile) ?: run {
            stop()
            return
        }
        if (profile.type == ProxyEntity.TYPE_BALANCER
            || profile.type == ProxyEntity.TYPE_CHAIN
            || profile.type == ProxyEntity.TYPE_CONFIG
        ) {
            stop()
            return
        }
        val host = try {
            profile.requireBean().serverAddress
        } catch (_: Throwable) {
            stop()
            return
        }
        if (host.isBlank()) {
            stop()
            return
        }
        val profileId = profile.id
        val generation = syncGeneration.incrementAndGet()
        syncJob?.cancel()
        syncJob = runOnDefaultDispatcher {
            val lookup = ServerGeoLookup.lookupForMockResult(host)
            val info = lookup.info ?: run {
                Logs.w("Mock location lookup failed for $host")
                lookup.ipinfoStatus?.let { showLookupFailed(it) }
                return@runOnDefaultDispatcher
            }
            if (!isCurrentSync(generation, profileId)) return@runOnDefaultDispatcher
            val lat = info.latitude ?: return@runOnDefaultDispatcher
            val lon = info.longitude ?: return@runOnDefaultDispatcher
            val (mockLat, mockLon) = randomNearby(lat, lon)
            Logs.d("Mock location resolved for profile=$profileId host=$host city=${info.city} lat=$lat lon=$lon")
            while (isCurrentSync(generation, profileId)) {
                setLocation(mockLat, mockLon)
                delay(LOCATION_REFRESH_INTERVAL)
            }
        }
    }

    private fun showLookupFailed(status: String) {
        runOnMainDispatcher {
            val message = if (status == "429") {
                SagerNet.application.getString(R.string.mock_geo_location_ipinfo_rate_limited)
            } else {
                SagerNet.application.getString(R.string.mock_geo_location_lookup_failed, status)
            }
            Toast.makeText(
                SagerNet.application,
                message,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun isCurrentSync(generation: Int, profileId: Long): Boolean {
        return syncGeneration.get() == generation
            && DataStore.mockGeoLocation
            && SagerNet.started
            && DataStore.startedProfile == profileId
            && isMockLocationAppAllowed()
    }

    private fun setLocation(latitude: Double, longitude: Double) {
        val lm = SagerNet.location
        try {
            for (provider in PROVIDERS) {
                try {
                    lm.addTestProvider(
                        provider,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE,
                    )
                } catch (_: IllegalArgumentException) {
                }
                lm.setTestProviderEnabled(provider, true)
                val location = Location(provider).apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    accuracy = 150f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                lm.setTestProviderLocation(provider, location)
            }
            Logs.d("Mock location set to $latitude, $longitude")
        } catch (e: SecurityException) {
            Logs.w(e)
            stop()
        } catch (e: Throwable) {
            Logs.w(e)
        }
    }

    fun stop() {
        syncGeneration.incrementAndGet()
        syncJob?.cancel()
        syncJob = null
        val lm = SagerNet.location
        for (provider in PROVIDERS) {
            try {
                if (lm.getProvider(provider) != null) {
                    lm.setTestProviderEnabled(provider, false)
                    lm.removeTestProvider(provider)
                }
            } catch (_: Throwable) {
            }
        }
    }
}
