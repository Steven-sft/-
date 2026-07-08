package com.drivingrecorder.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "驾驶记录仪 - 桌面预览版",
        state = rememberWindowState(width = 480.dp, height = 900.dp)
    ) {
        DrivingRecorderDesktop()
    }
}
