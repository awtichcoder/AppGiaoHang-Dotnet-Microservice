package com.deliveryapp.customer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DeliveryGreen,
    secondary = DeliveryGreenSoft,
    tertiary = DeliveryWarning,
    background = DeliveryText,
    surface = Color(0xFF1A2A23),
    onPrimary = Color.White,
    onSecondary = DeliveryText,
    onTertiary = DeliveryText,
    onBackground = Color.White,
    onSurface = Color.White,
    error = DeliveryDanger
)

private val LightColorScheme = lightColorScheme(
    primary = DeliveryGreen,
    secondary = DeliveryGreenSoft,
    tertiary = DeliveryWarning,
    background = DeliveryCanvas,
    surface = DeliverySurface,
    onPrimary = Color.White,
    onSecondary = DeliveryText,
    onTertiary = DeliveryText,
    onBackground = DeliveryText,
    onSurface = DeliveryText,
    outline = DeliveryBorder,
    error = DeliveryDanger
)

@Composable
fun DeliveryCustomerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
