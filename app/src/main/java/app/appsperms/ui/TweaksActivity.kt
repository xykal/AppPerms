package app.appsperms.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import app.appsperms.core.ShizukuBridge
import app.appsperms.databinding.ActivityTweaksBinding

/**
 * Layar Dedicated "Tweaks &amp; Resolusi Layar Aman"
 * Mengatur resolusi (wm size), kerapatan (wm density) dengan timer auto-revert 15 detik,
 * serta skala animasi global dalam satu antarmuka penuh.
 */
class TweaksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTweaksBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTweaksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.tweaksToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.tweaksToolbar.setNavigationOnClickListener { finish() }

        val canOperate = ShizukuBridge.isBinderAlive() && ShizukuBridge.hasPermission()

        TweaksSheet.bindViews(
            activity = this,
            canOperate = canOperate,
            dispStatus = binding.dispStatus,
            btnSizeChange = binding.btnSizeChange,
            btnSizeReset = binding.btnSizeReset,
            animStatus = binding.animStatus,
            btnAnimOff = binding.btnAnimOff,
            btnAnimHalf = binding.btnAnimHalf,
            btnAnimFull = binding.btnAnimFull,
            btnSnapshot = binding.btnSnapshot,
            btnRestoreSnapshot = binding.btnRestoreSnapshot,
            btnTweakDiagnostics = binding.btnTweakDiagnostics,
            onNotify = { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            },
        )
    }
}
