package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.screens.MainChatScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.AppStrings
import com.example.util.LocalAppLanguage
import com.example.util.LocalAppStrings
import com.example.util.CoilUtils

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoilUtils.initialize(this)
        enableEdgeToEdge()
        setContent {
            val themeMode by chatViewModel.themeMode.collectAsState()
            val appLanguage by chatViewModel.appLanguage.collectAsState()
            val strings = remember(appLanguage) { AppStrings.get(appLanguage) }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "light" -> false
                "system" -> systemDark
                else -> true
            }
            CompositionLocalProvider(
                LocalAppLanguage provides appLanguage,
                LocalAppStrings provides strings
            ) {
                MyApplicationTheme(darkTheme = isDark) {
                    MainChatScreen(viewModel = chatViewModel)
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("ZACK AI") }
}
