package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.helpnearby.data.remote.supabase.SupabaseSdkProvider
import com.example.helpnearby.feature.HelpNearbyRootApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabaseSdkProvider: SupabaseSdkProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supabaseSdkProvider.handleDeeplinks(intent)
        enableEdgeToEdge()
        setContent {
            HelpNearbyRootApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        supabaseSdkProvider.handleDeeplinks(intent)
    }
}
