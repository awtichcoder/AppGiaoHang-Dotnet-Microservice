package com.deliveryapp.shipper.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deliveryapp.shipper.data.repository.DemoScenario

@Composable
fun DemoScenarios(onDismiss: () -> Unit, onSelect: (DemoScenario) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Thử tình huống mô phỏng") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bật nhận đơn để thử lời mời. Các tình huống chỉ thay đổi dữ liệu dùng thử.")
                DemoScenario.entries.forEach { value ->
                    TextButton(onClick = { onSelect(value); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(value.label) }
                }
                Text("Để thử hết hạn: mở lời mời và đợi 30 giây. Để phục hồi sau lỗi mạng: chọn kết nối bình thường.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } })
}
