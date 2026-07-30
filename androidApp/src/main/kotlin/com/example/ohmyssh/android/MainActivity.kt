package com.example.ohmyssh.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.ohmyssh.App
import com.example.ohmyssh.platform.AndroidApp
import com.example.ohmyssh.serial.SerialRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var serviceController: SessionServiceController

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidApp.init(applicationContext)
        AndroidApp.attach(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        serviceController = SessionServiceController(applicationContext).also { it.attach() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    serviceController.onForeground(true)
                    CoroutineScope(Dispatchers.Default).launch { SerialRegistry.refresh() }
                }

                override fun onStop(owner: LifecycleOwner) {
                    serviceController.onForeground(false)
                }
            },
        )

        setContent { App() }
    }

    override fun onDestroy() {
        if (isFinishing) serviceController.detach()
        super.onDestroy()
    }
}
