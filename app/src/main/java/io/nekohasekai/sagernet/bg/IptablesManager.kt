package io.nekohasekai.sagernet.bg

import io.nekohasekai.sagernet.ktx.Logs

object IptablesManager {

    private const val CHAIN_NAME = "EXCLAVE"

    private val BYPASS_IPV4 = listOf(
        "0.0.0.0/8",
        "10.0.0.0/8",
        "100.64.0.0/10",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "224.0.0.0/4",
        "240.0.0.0/4",
    )

    private val BYPASS_IPV6 = listOf(
        "::1/128",
        "fe80::/10",
        "fc00::/7",
        "ff00::/8",
    )

    fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            process.exitValue() == 0 && output.contains("uid=0")
        } catch (e: Exception) {
            Logs.w("Root check failed", e)
            false
        }
    }

    fun setUp(
        transproxyPort: Int,
        dnsPort: Int,
        appUid: Int,
        bypassLan: Boolean,
        enableIPv6: Boolean,
    ) {
        val script = buildSetUpScript(transproxyPort, dnsPort, appUid, bypassLan, enableIPv6)
        executeRootScript(script)
    }

    fun tearDown(enableIPv6: Boolean = true) {
        val script = buildTearDownScript(enableIPv6)
        try {
            executeRootScript(script)
        } catch (e: Exception) {
            Logs.w("iptables teardown failed", e)
        }
    }

    internal fun buildSetUpScript(
        transproxyPort: Int,
        dnsPort: Int,
        appUid: Int,
        bypassLan: Boolean,
        enableIPv6: Boolean,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("set -e")

        // IPv4
        sb.appendIptablesSetUp("iptables", BYPASS_IPV4, transproxyPort, dnsPort, appUid, bypassLan)

        // IPv6
        if (enableIPv6) {
            sb.appendIptablesSetUp("ip6tables", BYPASS_IPV6, transproxyPort, dnsPort, appUid, bypassLan)
        }

        return sb.toString()
    }

    internal fun buildTearDownScript(enableIPv6: Boolean): String {
        val sb = StringBuilder()
        sb.appendIptablesTearDown("iptables")
        if (enableIPv6) {
            sb.appendIptablesTearDown("ip6tables")
        }
        return sb.toString()
    }

    private fun StringBuilder.appendIptablesSetUp(
        cmd: String,
        bypassRanges: List<String>,
        transproxyPort: Int,
        dnsPort: Int,
        appUid: Int,
        bypassLan: Boolean,
    ) {
        // Create or flush chain
        appendLine("$cmd -t nat -N $CHAIN_NAME 2>/dev/null || true")
        appendLine("$cmd -t nat -F $CHAIN_NAME")

        // Bypass own UID to prevent routing loops (must be first)
        appendLine("$cmd -t nat -A $CHAIN_NAME -m owner --uid-owner $appUid -j RETURN")

        // Redirect DNS (UDP port 53) before LAN bypass — DNS to router must be intercepted
        appendLine("$cmd -t nat -A $CHAIN_NAME -p udp --dport 53 -j REDIRECT --to-ports $dnsPort")

        // Bypass loopback interface
        appendLine("$cmd -t nat -A $CHAIN_NAME -o lo -j RETURN")

        // Bypass LAN/private addresses
        if (bypassLan) {
            for (range in bypassRanges) {
                appendLine("$cmd -t nat -A $CHAIN_NAME -d $range -j RETURN")
            }
        }

        // Redirect all TCP traffic
        appendLine("$cmd -t nat -A $CHAIN_NAME -p tcp -j REDIRECT --to-ports $transproxyPort")

        // Hook into OUTPUT chain (remove first to avoid duplicates)
        appendLine("$cmd -t nat -D OUTPUT -j $CHAIN_NAME 2>/dev/null || true")
        appendLine("$cmd -t nat -A OUTPUT -j $CHAIN_NAME")
    }

    private fun StringBuilder.appendIptablesTearDown(cmd: String) {
        appendLine("$cmd -t nat -D OUTPUT -j $CHAIN_NAME 2>/dev/null || true")
        appendLine("$cmd -t nat -F $CHAIN_NAME 2>/dev/null || true")
        appendLine("$cmd -t nat -X $CHAIN_NAME 2>/dev/null || true")
    }

    private fun executeRootScript(script: String) {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh"))
        process.outputStream.bufferedWriter().use { writer ->
            writer.write(script)
            writer.flush()
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val stderr = process.errorStream.bufferedReader().readText()
            throw RuntimeException("iptables script failed (exit $exitCode): $stderr")
        }
    }
}
