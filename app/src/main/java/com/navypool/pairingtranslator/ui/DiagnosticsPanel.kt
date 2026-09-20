package com.navypool.pairingtranslator.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.navypool.pairingtranslator.connection.LogLine
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * 診断情報カード。HS(翻訳サポ)のデバッグパネルと同じく
 * 黒地に緑モノスペースで表示する(設定画面の「▼ デバッグ」で使用)。
 */
@Composable
fun DiagnosticsDebugCard(onEnableBluetooth: () -> Unit) {
    val context = LocalContext.current
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
        color = Color(0xCC000000),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            diag.rows.forEach { row ->
                Text(
                    "${row.label}: ${row.value}",
                    color = if (row.warn) Color(0xFFFF5252) else Color(0xFF00FF00),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
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
