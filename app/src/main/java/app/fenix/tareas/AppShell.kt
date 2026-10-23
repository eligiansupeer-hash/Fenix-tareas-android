package app.fenix.tareas

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.fenix.tareas.ai.AiSettingsScreen
import app.fenix.tareas.ai.ChatScreen

@Composable
fun AppShell() {
    var destination by remember { mutableIntStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = destination == 0, onClick = { destination = 0 },
                icon = { Icon(Icons.Default.Chat, contentDescription = null) }, label = { Text("Chat") })
            NavigationBarItem(selected = destination == 1, onClick = { destination = 1 },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) }, label = { Text("IA y API") })
            NavigationBarItem(selected = destination == 2, onClick = { destination = 2 },
                icon = { Icon(Icons.Default.Task, contentDescription = null) }, label = { Text("Tareas") })
            NavigationBarItem(selected = destination == 3, onClick = { destination = 3 },
                icon = { Icon(Icons.Default.Assignment, contentDescription = null) }, label = { Text("Resultados") })
        }
    }) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when (destination) {
                0 -> ChatScreen()
                1 -> AiSettingsScreen()
                2 -> TaskHome()
                else -> ResultsScreen()
            }
        }
    }
}
