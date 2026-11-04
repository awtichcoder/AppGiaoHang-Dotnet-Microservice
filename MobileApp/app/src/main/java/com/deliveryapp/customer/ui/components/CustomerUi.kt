package com.deliveryapp.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.deliveryapp.customer.ui.theme.DeliveryBorder
import com.deliveryapp.customer.ui.theme.DeliveryDanger
import com.deliveryapp.customer.ui.theme.DeliveryGreen
import com.deliveryapp.customer.ui.theme.DeliveryGreenSoft
import com.deliveryapp.customer.ui.theme.DeliveryMuted
import com.deliveryapp.customer.ui.theme.DeliverySurface
import com.deliveryapp.customer.ui.theme.DeliveryText
import com.deliveryapp.customer.ui.theme.DeliveryWarning

@Composable
fun AppSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        content = content
    )
}

@Composable
fun DeliveryCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DeliverySurface),
        border = BorderStroke(1.dp, DeliveryBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun GreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DeliveryGreen)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f)
            )
        }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = error != null,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        if (error != null) {
            Text(text = error, color = DeliveryDanger, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DeliveryGreen)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (danger) DeliveryDanger else DeliveryBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (danger) DeliveryDanger else DeliveryText
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(color = DeliveryGreen)
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    DeliveryCard(modifier = modifier) {
        Text(text = "Không tải được dữ liệu", style = MaterialTheme.typography.titleMedium)
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
        PrimaryAction(label = "Thử lại", onClick = onRetry)
    }
}

@Composable
fun StatusBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (warning) DeliveryWarning else DeliveryGreenSoft, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(if (warning) Color(0xFFFFB020) else DeliveryGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (warning) "!" else "✓", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = DeliveryText)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = DeliveryText)
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    DeliveryCard(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
    }
}

@Composable
fun SessionExpiredDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirm,
        title = { Text("Phiên đăng nhập hết hạn") },
        text = { Text("Vui lòng đăng nhập lại để tiếp tục sử dụng ứng dụng.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Đăng nhập") }
        }
    )
}
