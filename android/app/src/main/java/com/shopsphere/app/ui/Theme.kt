package com.shopsphere.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF173B5E)
private val Teal = Color(0xFF0F766E)

private val Colors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F2),
    onPrimaryContainer = Navy,
    secondary = Teal,
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),
    surface = Color.White,
)

@Composable
fun ShopSphereTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
