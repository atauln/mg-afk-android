package com.mgafk.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.mgafk.app.auth.OAuthActivity
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.screens.MainScreen
import com.mgafk.app.ui.theme.MgAfkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingOAuthSessionId: String? = null

    private val oauthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val token = result.data?.getStringExtra(OAuthActivity.EXTRA_TOKEN)
        val sessionId = pendingOAuthSessionId
        pendingOAuthSessionId = null
        if (!token.isNullOrBlank() && sessionId != null) {
            viewModel.setToken(sessionId, token)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MgAfkTheme {
                MainScreen(
                    viewModel = viewModel,
                    onLoginRequest = { sessionId ->
                        pendingOAuthSessionId = sessionId
                        oauthLauncher.launch(Intent(this, OAuthActivity::class.java))
                    },
                )
            }
        }
    }
}
