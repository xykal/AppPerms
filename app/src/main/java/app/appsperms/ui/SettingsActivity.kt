package app.appsperms.ui

import android.os.Bundle
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import app.appsperms.BuildConfig
import app.appsperms.R
import app.appsperms.core.AppLanguage
import app.appsperms.core.Settings
import app.appsperms.core.SortMode
import app.appsperms.databinding.ActivitySettingsBinding

/**
 * Layar Dedicated "Pengaturan"
 * Bahasa aplikasi, peringatan mode berisiko, peringatan shared UID, dan pengurutan default.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.settingsToolbar.setNavigationOnClickListener { finish() }

        // Nilai Awal
        when (Settings.language(this)) {
            AppLanguage.SYSTEM -> binding.langSystem.isChecked = true
            AppLanguage.INDONESIAN -> binding.langId.isChecked = true
            AppLanguage.ENGLISH -> binding.langEn.isChecked = true
        }
        binding.switchRisk.isChecked = Settings.confirmRiskyModes(this)
        binding.switchSharedUid.isChecked = Settings.warnSharedUid(this)
        when (Settings.defaultSort(this)) {
            SortMode.NAME -> binding.sortName.isChecked = true
            SortMode.STATUS -> binding.sortStatus.isChecked = true
        }
        binding.settingsFooter.text = getString(
            R.string.settings_footer,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )

        // Event Listeners
        binding.langGroup.setOnCheckedChangeListener { _, checkedId ->
            val language = when (checkedId) {
                R.id.langId -> AppLanguage.INDONESIAN
                R.id.langEn -> AppLanguage.ENGLISH
                else -> AppLanguage.SYSTEM
            }
            if (language != Settings.language(this)) {
                Settings.setLanguage(this, language)
                Snackbar.make(binding.root, getString(R.string.snack_language_set, labelOf(language)), Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.switchRisk.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            Settings.setConfirmRiskyModes(this, checked)
            Snackbar.make(
                binding.root,
                if (checked) R.string.snack_risk_on else R.string.snack_risk_off,
                Snackbar.LENGTH_SHORT,
            ).show()
        }

        binding.switchSharedUid.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            Settings.setWarnSharedUid(this, checked)
        }

        binding.sortGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.sortStatus) SortMode.STATUS else SortMode.NAME
            Settings.setDefaultSort(this, mode)
        }
    }

    private fun labelOf(language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> getString(R.string.settings_lang_system)
        AppLanguage.INDONESIAN -> getString(R.string.settings_lang_id)
        AppLanguage.ENGLISH -> getString(R.string.settings_lang_en)
    }
}
