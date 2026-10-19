package com.navypool.pairingtranslator.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navypool.pairingtranslator.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** ネットワーク種別。ピクト行の左アイコンに使う */
enum class PictNetState { WIFI, CELL, NONE }

/**
 * 左カラム最上部のピクト行: 左=電波 / 中央=時計 / 右=電池残量。
 * 背景はステータス色で塗られたカラムのため、白単色で描画する。
 */
@Composable
fun PictogramBar(modifier: Modifier = Modifier, tint: Color = Color.White) {
    val net = rememberNetState()
    val battery = rememberBatteryState()
    val clock = rememberClockText()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(net.iconRes()),
            contentDescription = when (net) {
                PictNetState.WIFI -> "Wi-Fi接続中"
                PictNetState.CELL -> "モバイル回線接続中"
                PictNetState.NONE -> "ネットワーク未接続"
            },
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            clock,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
        Icon(
            painter = painterResource(batteryIconRes(battery.level, battery.charging)),
            contentDescription = if (battery.charging) "充電中" else "電池残量 ${(battery.level * 100).toInt()}%",
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun PictNetState.iconRes(): Int = when (this) {
    PictNetState.WIFI -> R.drawable.ic_pict_wifi
    PictNetState.CELL -> R.drawable.ic_pict_cell
    PictNetState.NONE -> R.drawable.ic_pict_globe_off
}

private fun batteryIconRes(level: Float, charging: Boolean): Int = when {
    charging -> R.drawable.ic_battery_frame_bolt
    level >= 0.99f -> R.drawable.ic_battery_frame_full
    // 残量を6段階のフレームに丸めて表示する
    else -> when (ceil(level * 6).toInt().coerceIn(1, 6)) {
        1 -> R.drawable.ic_battery_frame_1
        2 -> R.drawable.ic_battery_frame_2
        3 -> R.drawable.ic_battery_frame_3
        4 -> R.drawable.ic_battery_frame_4
        5 -> R.drawable.ic_battery_frame_5
        else -> R.drawable.ic_battery_frame_6
    }
}

/** 既定ネットワークの種別を監視する(Wi-Fi優先、無ければ携帯通信、無ければNONE) */
@Composable
private fun rememberNetState(): PictNetState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PictNetState.NONE) }

    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun recheck() {
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            state = when {
                caps == null -> PictNetState.NONE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> PictNetState.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> PictNetState.CELL
                else -> PictNetState.NONE
            }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = recheck()
            override fun onLost(network: Network) = recheck()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = recheck()
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        recheck()
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return state
}

private data class PictBatteryState(val level: Float, val charging: Boolean)

/** 電池残量とAC/USB接続状態。ACTION_BATTERY_CHANGED(sticky)で受信する */
@Composable
private fun rememberBatteryState(): PictBatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PictBatteryState(1f, false)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val intent = i ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                if (level >= 0 && scale > 0) {
                    state = PictBatteryState(level / scale.toFloat(), plugged != 0)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** 現在時刻(h:mm AM/PM形式)。1秒ごとに更新する */
@Composable
private fun rememberClockText(): String {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val format = SimpleDateFormat("h:mm a", Locale.US)
        while (true) {
            text = format.format(Date())
            delay(1_000)
        }
    }
    return text
}
