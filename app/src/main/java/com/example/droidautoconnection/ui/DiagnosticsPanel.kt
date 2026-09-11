package com.example.droidautoconnection.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidautoconnection.connection.LogLine
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

@Composable
fun DiagnosticsPanel(onEnableBluetooth: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    // ON_RESUME相当の再描画用キー(クイック設定でラジオを切り替えた場合の反映)
    var refreshKey by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val diag = remember(refreshKey) { buildDiagnostics(context) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Text(
                    if (expanded) "▼ 診断情報" else "▲ 診断情報",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    diag.rows.forEach { row ->
                        Row(Modifier.padding(top = 4.dp)) {
                            Text(
                                row.label,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                row.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.warn) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (!diag.bluetoothEnabled) {
                        Button(
                            onClick = onEnableBluetooth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Text("BluetoothをONにする")
                        }
                    }
                }
            }
        }
    }
}

private data class DiagRow(val label: String, val value: String, val warn: Boolean)

private data class Diagnostics(
    val rows: List<DiagRow>,
    val bluetoothEnabled: Boolean,
)

private fun buildDiagnostics(context: Context): Diagnostics {
    val rows = mutableListOf<DiagRow>()

    rows += DiagRow("端末名", Build.MODEL, false)
    rows += DiagRow("Android", "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})", false)
    val versionName = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }
    rows += DiagRow("アプリバージョン", versionName, false)

    val btEnabled = try {
        val bm = context.getSystemService(BluetoothManager::class.java)
        bm?.adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }
    rows += DiagRow("Bluetooth", if (btEnabled) "ON" else "OFF", !btEnabled)

    val wifiEnabled = try {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
    } catch (_: Exception) {
        false
    }
    // Wi-FiはオフでもBLE発見で動作するため警告ではなく参考情報
    rows += DiagRow("Wi-Fi", if (wifiEnabled) "ON" else "OFF (BLEのみで動作可)", false)

    val nanSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
    rows += DiagRow("Wi-Fi Aware (NAN)", if (nanSupported) "対応" else "非対応 (PoCでは未使用)", false)

    val gmsCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    rows += if (gmsCode == ConnectionResult.SUCCESS) {
        DiagRow("Google Play Services", "利用可能", false)
    } else {
        DiagRow("Google Play Services", "利用不可 (code=$gmsCode)", true)
    }

    return Diagnostics(rows, btEnabled)
}

@Composable
fun LogViewer(logs: List<LogLine>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size, expanded) {
        if (expanded && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Surface(
        color = Color(0xFF101418),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                if (expanded) "▼ ログ" else "▲ ログ (${logs.size}行)",
                color = Color(0xFF9AB4C8),
                fontSize = 13.sp,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (expanded) {
                HorizontalDivider(color = Color(0xFF33414D), modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(180.dp),
                ) {
                    items(logs) { line ->
                        Text(
                            "${line.time} ${line.text}",
                            color = Color(0xFFC8D8E4),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
