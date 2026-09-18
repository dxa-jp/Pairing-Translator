package com.example.droidautoconnection.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.droidautoconnection.Permissions
import com.example.droidautoconnection.connection.PeerLinkManager

@Composable
fun AppScreen(
    peerLink: PeerLinkManager,
    onEnableBluetooth: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var permsGranted by remember { mutableStateOf(Permissions.granted(context)) }

    // 設定画面から戻った場合などに再チェックする。
    // またON_RESUMEでpeerLink.resume()を呼び、スリープ復帰後に
    // GMSが止めていたadvertise/discoverを張り直す
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permsGranted = Permissions.granted(context)
                peerLink.resume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4C7C7C),
            background = Color(0xFFE8F0E8),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1A1A),
        ),
    ) {
        // 暗色の背景はシステムバー(ピクト)領域の帯として表示され、
        // コンテンツ本体は内側の画面が各自の背景色で描画する
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1B1B1F)) {
            if (!permsGranted) {
                PermissionScreen(
                    onRequestGranted = { permsGranted = true },
                )
            } else {
                LaunchedEffect(Unit) { peerLink.start() }
                ConversationScreen(
                    peerLink = peerLink,
                    onEnableBluetooth = onEnableBluetooth,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequestGranted: () -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            onRequestGranted()
        } else {
            denied = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("権限の許可", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "このアプリは近くの端末を自動検出して接続し、マイクの音声を" +
                "リアルタイムで翻訳して相手の端末に送ります。" +
                "最初に1回だけ権限の許可が必要です(マイク・近くのデバイス)。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        if (denied) {
            Text(
                "許可されませんでした。ペアリングやPIN入力は不要ですが、" +
                    "端末検出と音声認識のための権限が必要です。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = { launcher.launch(Permissions.required().toTypedArray()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("権限を許可する")
        }
    }
}
