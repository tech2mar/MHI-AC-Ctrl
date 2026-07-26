package de.tech2mar.openwebui.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.tech2mar.openwebui.AppContainer
import de.tech2mar.openwebui.ui.chat.ChatScreen
import de.tech2mar.openwebui.ui.chat.ChatViewModel
import de.tech2mar.openwebui.ui.settings.SettingsScreen
import de.tech2mar.openwebui.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CHAT = "chat"

@Composable
fun AppNavHost(container: AppContainer) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val settings = container.settingsRepository.settings.first()
        startDestination = if (settings.isConfigured) ROUTE_CHAT else ROUTE_SETTINGS
    }

    val resolvedStart = startDestination
    if (resolvedStart == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = resolvedStart) {
        composable(ROUTE_SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(container.settingsRepository, container.api)
            )
            SettingsScreen(
                viewModel = viewModel,
                onContinueToChat = {
                    navController.navigate(ROUTE_CHAT) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(ROUTE_CHAT) {
            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(container.settingsRepository, container.api)
            )
            ChatScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }
    }
}
