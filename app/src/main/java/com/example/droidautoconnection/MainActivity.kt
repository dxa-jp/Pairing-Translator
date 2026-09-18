package com.example.droidautoconnection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.example.droidautoconnection.connection.PeerLinkManager
import com.example.droidautoconnection.data.BackendClient
import com.example.droidautoconnection.ui.AppScreen

class MainActivity : ComponentActivity() {

    lateinit var peerLink: PeerLinkManager
        private set

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 結果はON_RESUMEでの再チェックで反映される
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // アプリ前面表示中は画面をスリープさせない(テスト用のAlways Display ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // edge-to-edgeを明示的に有効化し、キーボード表示時のシステム側
        // リサイズ(adjustResize)による二重の上げ込みを防ぐ。インセット処理は
        // Compose側(windowInsetsPadding/imePadding)に一本化する
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // ステータスバー・ナビゲーションバーのピクトを白(暗い背景用)にする
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        peerLink = PeerLinkManager(this)
        // 設定画面で変更したサーバーURLを反映(HSのendpoint_urlと同じ運用)
        val prefs = getSharedPreferences("poc_prefs", Context.MODE_PRIVATE)
        BackendClient.baseUrl = prefs.getString("server_url", null)?.takeIf { it.isNotBlank() }
            ?: BackendClient.DEFAULT_BASE_URL
        setContent {
            AppScreen(
                peerLink = peerLink,
                onEnableBluetooth = {
                    try {
                        enableBtLauncher.launch(
                            Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE),
                        )
                    } catch (_: SecurityException) {
                        // 権限未付与では発生しないはず(権限付与後にのみ表示する)
                    }
                },
                onOpenSettings = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                },
            )
        }
    }

    override fun onDestroy() {
        peerLink.stop()
        super.onDestroy()
    }
}
