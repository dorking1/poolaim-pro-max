package com.poolaim.promax

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.poolaim.promax.ui.theme.PoolAimTheme
import com.poolaim.promax.ui.navigation.PoolAimNavHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoolAimTheme {
                PoolAimNavHost(activity = this)
            }
        }
    }
}
