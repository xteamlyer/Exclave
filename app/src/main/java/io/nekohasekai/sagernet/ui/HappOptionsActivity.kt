package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.HappSpoof

class HappOptionsActivity : ThemedActivity(R.layout.layout_config_settings) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.happ_options)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (DataStore.subscriptionHappUserId.isNullOrEmpty()) {
            DataStore.subscriptionHappUserId = HappSpoof.randomUserId()
        }
        if (DataStore.subscriptionHappHwid.isNullOrEmpty()) {
            DataStore.subscriptionHappHwid = HappSpoof.randomHwid()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, HappOptionsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    class HappOptionsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            addPreferencesFromResource(R.xml.happ_options)

            val userId = findPreference<EditTextPreference>(Key.SUBSCRIPTION_HAPP_USER_ID)!!
            val hwid = findPreference<EditTextPreference>(Key.SUBSCRIPTION_HAPP_HWID)!!
            val randomize = findPreference<Preference>(Key.SUBSCRIPTION_HAPP_RANDOMIZE_IDS)!!
            randomize.setOnPreferenceClickListener {
                val newUid = HappSpoof.randomUserId()
                val newHwid = HappSpoof.randomHwid()
                DataStore.subscriptionHappUserId = newUid
                DataStore.subscriptionHappHwid = newHwid
                userId.text = newUid
                hwid.text = newHwid
                true
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
                insets
            }
        }
    }
}
