package com.deliveryapp.shipper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DeliveryGreen,
    primaryContainer = DeliveryGreenSoft,
    onPrimaryContainer = DeliveryGreenDark,
    secondary = DeliveryGreenSoft,
    secondaryContainer = DeliveryGreenSoft,
    onSecondaryContainer = DeliveryGreenDark,
    tertiary = DeliveryWarning,
    background = DeliveryCanvas,
    surface = DeliverySurface,
    onPrimary = Color.White,
    onSecondary = DeliveryText,
    onTertiary = DeliveryText,
    onBackground = DeliveryText,
    onSurface = DeliveryText,
    surfaceVariant = DeliveryGreenSoft,
    surfaceContainer = DeliveryCanvas,
    surfaceContainerHigh = DeliverySurface,
    surfaceContainerHighest = DeliveryGreenSoft,
    surfaceTint = DeliveryGreen,
    onSurfaceVariant = DeliveryMuted,
    outline = DeliveryBorder,
    error = DeliveryDanger
)

@Composable
fun DeliveryShipperTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
