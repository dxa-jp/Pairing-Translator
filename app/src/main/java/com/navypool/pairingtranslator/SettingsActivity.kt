package com.navypool.pairingtranslator

import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.navypool.pairingtranslator.data.BackendClient
import com.navypool.pairingtranslator.ui.DiagnosticsDebugCard

private val HsBackground = Color(0xFFE8F0E8)
private val HsPillWhite = Color(0xB8FFFFFF)
private val HsTextMain = Color(0xFF1A1A1A)
private val HsTextSub = Color(0xFF5F6368)

class SettingsActivity : ComponentActivity() {

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 結果は診断カードのON_RESUME再描画で反映される
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        // メイン画面と同じくフルスクリーン(スワイプで一時表示)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF4C7C7C),
                    background = HsBackground,
                    surface = Color.White,
                    onSurface = HsTextMain,
                ),
            ) {
                SettingsScreen(
                    onBack = { finish() },
                    onEnableBluetooth = {
                        try {
                            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } catch (_: SecurityException) {
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onEnableBluetooth: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("poc_prefs", Context.MODE_PRIVATE) }
    var serverUrl by remember {
        mutableStateOf(
            prefs.getString("server_url", null)?.takeIf { it.isNotBlank() }
                ?: BackendClient.DEFAULT_BASE_URL,
        )
    }
    val deviceId = remember {
        prefs.getString("device_id", null) ?: "(未生成 — 一度メイン画面を起動すると生成されます)"
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .background(HsBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = HsPillWhite,
                shape = RoundedCornerShape(100),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .clickable { onBack() },
            ) {
                Text(
                    "← 戻る",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = HsTextMain,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text("ペアリング翻訳 設定", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HsTextMain)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            SectionHeader("▼ 接続")
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { text ->
                    serverUrl = text
                    prefs.edit().putString("server_url", text).apply()
                    BackendClient.baseUrl = text.ifBlank { BackendClient.DEFAULT_BASE_URL }
                },
                label = { Text("サーバーURL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = {
                serverUrl = BackendClient.DEFAULT_BASE_URL
                prefs.edit().putString("server_url", BackendClient.DEFAULT_BASE_URL).apply()
                BackendClient.baseUrl = BackendClient.DEFAULT_BASE_URL
            }) {
                Text("デフォルトに戻す")
            }

            SectionHeader("▼ デバイス")
            Text("デバイスID (管理画面の端末承認に使用)", fontSize = 13.sp, color = HsTextSub)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    deviceId,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HsTextMain,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("device_id", deviceId))
                    Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                }) {
                    Text("コピー")
                }
            }

            SectionHeader("▼ デバッグ")
            DiagnosticsDebugCard(onEnableBluetooth = onEnableBluetooth)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF555555),
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}
