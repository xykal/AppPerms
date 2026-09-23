package app.appsperms.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.appsperms.BuildConfig
import app.appsperms.R
import app.appsperms.databinding.ActivityAboutBinding

/**
 * Layar Lengkap "Tentang AppsPerms" & Ekosistem XyVerse
 * Menampilkan audit privasi, tim kontributor, dan kartu interaktif aplikasi XyVerse.
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

        // Tautan Aksi Utama
        binding.btnAboutWebsite.setOnClickListener { openUrl("https://appsperms.xyverse.my.id/") }
        binding.btnAboutSource.setOnClickListener { openUrl("https://github.com/xykalnotkel/OverlayOps") }
        binding.btnAboutUpdate.setOnClickListener { openUrl("https://github.com/xykalnotkel/OverlayOps/releases/latest") }
        binding.btnAboutChannel.setOnClickListener { openUrl("https://t.me/xyverse") }
        binding.btnAboutFaq.setOnClickListener { openUrl("https://appsperms.xyverse.my.id/#faq") }
        binding.btnAboutFeedback.setOnClickListener { openUrl("https://github.com/xykalnotkel/OverlayOps/issues") }

        // Ekosistem XyVerse (Horizontal Carousel Cards)
        binding.cardEcosystemAppsPerms.setOnClickListener {
            openUrl("https://appsperms.xyverse.my.id/")
        }
        binding.cardEcosystemXyDesk.setOnClickListener {
            openUrl("https://github.com/xykalnotkel/xydesk")
        }
        binding.cardEcosystemXyCloud.setOnClickListener {
            openUrl("https://github.com/xykalnotkel")
        }
        binding.cardEcosystemXyStudio.setOnClickListener {
            openUrl("https://github.com/xykalnotkel")
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
