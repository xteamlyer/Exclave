package io.nekohasekai.sagernet.bg

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.net.Network
import android.os.PowerManager
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TransproxyService : Service(),
    BaseService.Interface,
    LocalResolver {

    override val data = BaseService.Data(this)
    override val tag: String get() = "SagerNetTransproxyService"
    override fun createNotification(profileName: String): ServiceNotification =
        ServiceNotification(this, profileName, "service-transproxy", true)
    override var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    override var underlyingNetwork: Network? = null
    private var networkListenerIsRunning = false

    class RootUnavailableException : Exception(), BaseService.ExpectedException {
        override fun getLocalizedMessage() =
            SagerNet.application.getString(R.string.root_not_available)
    }

    override suspend fun preInit() {
        if (!IptablesManager.checkRootAccess()) {
            throw RootUnavailableException()
        }
        networkListenerIsRunning = true
        DefaultNetworkListener.start(this) {
            if (networkListenerIsRunning) {
                SagerNet.reloadNetwork(it)
                underlyingNetwork = it
            }
        }
    }

    override suspend fun startProcesses() {
        data.proxy!!.v2rayPoint.withLocalResolver(this)
        super.startProcesses()

        val appUid = applicationInfo.uid
        IptablesManager.setUp(
            transproxyPort = DataStore.transproxyPort,
            dnsPort = DataStore.localDNSPort,
            appUid = appUid,
            bypassLan = DataStore.bypassLan,
            enableIPv6 = DataStore.enableVPNInterfaceIPv6Address,
        )
        Logs.i("Transparent proxy iptables rules set up (port=${DataStore.transproxyPort}, dns=${DataStore.localDNSPort}, uid=$appUid)")
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun killProcesses() {
        data.proxy?.v2rayPoint?.withLocalResolver(null)
        IptablesManager.tearDown(enableIPv6 = DataStore.enableVPNInterfaceIPv6Address)
        Logs.i("Transparent proxy iptables rules cleaned up")
        super.killProcesses()
        networkListenerIsRunning = false
        GlobalScope.launch(Dispatchers.Default) { DefaultNetworkListener.stop(this) }
    }

    @SuppressLint("WakelockTimeout")
    override fun acquireWakeLock() {
        wakeLock = SagerNet.power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sagernet:transproxy")
            .apply { acquire() }
    }

    override fun onBind(intent: Intent) = super.onBind(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        super<BaseService.Interface>.onStartCommand(intent, flags, startId)

    override fun onDestroy() {
        super.onDestroy()
        data.binder.close()
    }
}
