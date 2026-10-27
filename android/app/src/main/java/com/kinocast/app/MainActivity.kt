package com.kinocast.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
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

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
            if (audioGranted) {
                launchScreenCapture()
            } else {
                Toast.makeText(this, "Kino ovozini yozib olish uchun ruxsat kerak!", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateIpAddress()

        binding.btnToggleStream.setOnClickListener {
            if (!isStreaming) {
                checkPermissionsAndStart()
            } else {
                stopStreamService()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateIpAddress()
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            launchScreenCapture()
        }
    }

    private fun launchScreenCapture() {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        requestMediaProjection.launch(intent)
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
            binding.tvStatus.text = "Uzatish Faol (16:9 kutilmoqda)"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
            binding.btnToggleStream.text = "Uzatishni To'xtatish"
            binding.btnToggleStream.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            binding.tvStatus.text = "To'xtatilgan"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.amber))
            binding.btnToggleStream.text = "Uzatishni Boshlash"
            binding.btnToggleStream.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        }
    }

    private fun updateIpAddress() {
        val ip = getLocalIpAddress()
        binding.tvAddress.text = "http://$ip:8080"
    }

    private fun getLocalIpAddress(): String {
        try {
            // Hotspot odatda 192.168.43.1 bo'ladi
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return "192.168.43.1"
    }
}
