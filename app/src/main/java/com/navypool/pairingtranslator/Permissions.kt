package com.navypool.pairingtranslator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 必要なランタイム権限をAPIレベルごとに組み立てる。
 * - RECORD_AUDIO: 音声認識(全バージョン共通)
 * - API 33+ : BLUETOOTH_SCAN/ADVERTISE/CONNECT + NEARBY_WIFI_DEVICES
 * - API 31-32: BLUETOOTH_SCAN/ADVERTISE/CONNECT
 * - API 30以下: ACCESS_FINE_LOCATION
 */
object Permissions {

    fun required(): List<String> {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            perms += listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms
    }

    fun granted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
