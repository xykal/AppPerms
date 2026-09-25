package app.appsperms.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.appsperms.BuildConfig
import app.appsperms.R
import app.appsperms.databinding.ActivityAboutBinding

/**
 * Tentang AppsPerms - Redesign v4.1
 * - Logo Apps di tengah (hero 88dp)
 * - XyVerse bulat 88dp white circle + list ke samping (pills)
 * - Donate / Dukung Kami: Saweria, GitHub Sponsors, Trakteer, XyVerse
 * - Ekosistem list samping horizontal
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.aboutToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.aboutToolbar.setNavigationOnClickListener { finish() }

        binding.aboutVersionText.text = getString(
            R.string.about_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )

        // Main links
        binding.btnAboutWebsite.setOnClickListener { openUrl("https://appsperms.xyverse.my.id/") }
        binding.btnAboutSource.setOnClickListener { openUrl("https://github.com/xykal/AppPerms") }
        binding.btnAboutUpdate.setOnClickListener { openUrl("https://github.com/xykal/AppPerms/releases/latest") }
        binding.btnAboutFaq.setOnClickListener { openUrl("https://appsperms.xyverse.my.id/#faq") }

        // XyVerse logo click -> xyverse.my.id (will show real logo from brand/mark-flat-purple.webp in web, placeholder in app until company logo asset ready)
        binding.xyverseLogo.setOnClickListener { openUrl("https://www.xyverse.my.id/") }
        binding.root.findViewById<android.view.View>(R.id.xyverseCompanyBadge)?.setOnClickListener { openUrl("https://www.xyverse.my.id/") }

        // Donate / Dukung Kami
        binding.btnDonateSaweria.setOnClickListener { openUrl("https://saweria.co/xykal") }
        binding.btnDonateGithub.setOnClickListener { openUrl("https://github.com/sponsors/xykal") }
        binding.btnDonateTrakteer.setOnClickListener { openUrl("https://trakteer.id/xykal/tip") }
        binding.btnAboutXyverse.setOnClickListener { openUrl("https://www.xyverse.my.id/") }

        // Ecosystem horizontal cards
        binding.cardEcosystemAppsPerms.setOnClickListener {
            openUrl("https://appsperms.xyverse.my.id/")
        }
        binding.cardEcosystemXyDesk.setOnClickListener {
            openUrl("https://github.com/xykal/XyDesk")
        }
        binding.cardEcosystemXyCloud.setOnClickListener {
            openUrl("https://github.com/xykal")
        }
        binding.cardEcosystemXyStudio.setOnClickListener {
            openUrl("https://github.com/xykal")
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
}
