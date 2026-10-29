package com.natantec.mobiletube

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.natantec.mobiletube.data.YoutubeRepository
import com.natantec.mobiletube.ui.AppRoot
import com.natantec.mobiletube.ui.MobileTubeTheme

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handle(intent)
        setContent { MobileTubeTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Abre links do YouTube (clicados ou compartilhados) direto no player. */
    private fun handle(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val id = YoutubeRepository.videoId(text) ?: return
        vm.openUrl("https://www.youtube.com/watch?v=$id")
    }
}
