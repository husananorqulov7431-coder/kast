package com.kinocast.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kinocast.app.databinding.ActivityMainBinding
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isStreaming = false

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val requestMediaProjection =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startServiceWithProjection(result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, "Ekran uzatishga ruxsat berilmadi", Toast.LENGTH_SHORT).show()
                updateUiState(false)
            }
        }

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchScreenCapture()
            } else {
                Toast.makeText(this, "Kino ovozini uzatish uchun ruxsat kerak!", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateIpAddress()
        checkPermissionsUI()

        binding.btnToggleStream.setOnClickListener {
            if (!isStreaming) {
                checkAndStart()
            } else {
                stopStreamService()
            }
        }

        binding.btnBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        binding.btnOverlay.setOnClickListener {
            requestOverlayPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        updateIpAddress()
        checkPermissionsUI()
        if (ScreenCastService.isRunning) {
            updateUiState(true)
        }
    }

    private fun checkAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            launchScreenCapture()
        }
    }

    private fun launchScreenCapture() {
        try {
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            requestMediaProjection.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Xatolik: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startServiceWithProjection(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCastService::class.java).apply {
            action = ScreenCastService.ACTION_START
            putExtra(ScreenCastService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCastService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        updateUiState(true)
    }

    private fun stopStreamService() {
        val serviceIntent = Intent(this, ScreenCastService::class.java).apply {
            action = ScreenCastService.ACTION_STOP
        }
        startService(serviceIntent)
        updateUiState(false)
    }

    private fun updateUiState(streaming: Boolean) {
        isStreaming = streaming
        if (streaming) {
            binding.tvStatus.text = "🟢 Uzatish Faol (16:9 kino kutilmoqda)"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
            binding.btnToggleStream.text = "⏹️ Uzatishni To'xtatish"
            binding.btnToggleStream.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            binding.tvStatus.text = "To'xtatilgan"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.amber))
            binding.btnToggleStream.text = "🎬 Uzatishni Boshlash"
            binding.btnToggleStream.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            } else {
                Toast.makeText(this, "Batareya cheklovi olib tashlangan!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                Toast.makeText(this, "Ilovalar ustidan ko'rsatish ruxsati bor!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                binding.btnBattery.text = "✅ Batareya (Ruxsat bor)"
            }
            if (Settings.canDrawOverlays(this)) {
                binding.btnOverlay.text = "✅ Ilovalar Ustidan (Ruxsat bor)"
            }
        }
    }

    private fun updateIpAddress() {
        val ip = getLocalIpAddress()
        binding.tvAddress.text = "http://$ip:8080"
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("172.") || host.startsWith("192.168.") || host.startsWith("10.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return "172.30.121.120"
    }
}
