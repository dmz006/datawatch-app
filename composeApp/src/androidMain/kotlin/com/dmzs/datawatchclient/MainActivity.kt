package com.dmzs.datawatchclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dmzs.datawatchclient.ui.AppRoot

/**
 * Launch Activity. Hands off to the Compose navigation root — see
 * [com.dmzs.datawatchclient.ui.AppRoot].
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}
