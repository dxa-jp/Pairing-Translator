@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.droidautoconnection.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidautoconnection.connection.PeerLinkManager
import com.example.droidautoconnection.connection.SpeechEntry
import com.example.droidautoconnection.utils.Languages
import com.example.droidautoconnection.voice.VoiceState

/**
 * メイン画面: 自分の入力言語設定 + 音声翻訳の会話ビュー。
 * テキスト入力はなく、接続中は常時リスニングで音声翻訳が流れる。
 */
@Composable
fun ConversationScreen(
    peerLink: PeerLinkManager,
    onEnableBluetooth: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(peerLink.entries.size) {
        if (peerLink.entries.isNotEmpty()) {
            listState.animateScrollToItem(peerLink.entries.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // ステータスバー(時計などのピクト)・ナビゲーションバー・
            // カットアウトと重ならないよう余白を確保する(edge-to-edge対策)。
            // 余白部分は外側の暗色Surfaceがピクトの背景として見える
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        StatusHeader(peerLink)
        LanguageSelector(peerLink)
        DiagnosticsPanel(onEnableBluetooth)
        LogViewer(peerLink.logs)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(peerLink.entries) { entry ->
                SpeechBubble(entry, peerLang = peerLink.peer?.lang)
            }
        }
    }
}

@Composable
private fun StatusHeader(peerLink: PeerLinkManager) {
    val (label, color) = when (peerLink.state) {
        PeerLinkManager.State.IDLE -> "停止" to Color(0xFF9E9E9E)
        PeerLinkManager.State.SEARCHING -> "相手を探索中…" to Color(0xFFF57C00)
        PeerLinkManager.State.CONNECTING -> "接続中…" to Color(0xFF1976D2)
        PeerLinkManager.State.CONNECTED -> "接続済み" to Color(0xFF388E3C)
    }
    val voiceLabel = when (peerLink.voiceState) {
        VoiceState.IDLE -> ""
        VoiceState.REQUESTING_KEY -> "・キー取得中…"
        VoiceState.LISTENING -> "・🎧音声認識中"
        VoiceState.ERROR -> "・音声エラー"
    }
    val peer = peerLink.peer
    val myDisplay = Languages.codeToDisplayMap[peerLink.myLang] ?: peerLink.myLang
    val pairText = when {
        peerLink.state == PeerLinkManager.State.CONNECTED && peer != null -> {
            val peerDisplay = Languages.codeToDisplayMap[peer.lang] ?: peer.lang
            "自分: $myDisplay → 相手: $peerDisplay  |  相手端末: ${peer.endpointName}"
        }
        else -> "自分: $myDisplay  |  同じアプリを起動した端末を自動で探します"
    }

    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(color, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(label + voiceLabel, color = Color.White, fontSize = 13.sp)
                }
                Text(
                    "  サーバー: ${peerLink.serverState}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                pairText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 自分の入力言語を選択するピッカー。変更は保存され、次回接続時から反映される。
 */
@Composable
private fun LanguageSelector(peerLink: PeerLinkManager) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDisplay = Languages.codeToDisplayMap[peerLink.myLang] ?: peerLink.myLang

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = selectedDisplay,
            onValueChange = {},
            readOnly = true,
            label = { Text("自分の入力言語 (変更は次回接続時から反映)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Languages.list.forEach { info ->
                DropdownMenuItem(
                    text = { Text(info.displayName, fontSize = 14.sp) },
                    onClick = {
                        peerLink.myLang = info.code
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SpeechBubble(entry: SpeechEntry, peerLang: String?) {
    if (entry.isSystem) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                entry.original,
                fontSize = 12.sp,
                color = Color(0xFF757575),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        return
    }

    // 自分の発話: 原文(自分の言語)が主、訳文が副。
    // 相手の発話: 訳文(自分の言語)が主、原文が副。
    val primary = if (entry.mine) entry.original else entry.translation.ifEmpty { entry.original }
    val secondary = if (entry.mine) entry.translation else entry.original

    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (entry.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = if (entry.mine) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (entry.mine) 12.dp else 2.dp,
                bottomEnd = if (entry.mine) 2.dp else 12.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    primary.ifEmpty { "…" },
                    fontSize = 17.sp,
                    color = if (entry.mine) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
                )
                if (secondary.isNotEmpty()) {
                    Text(
                        secondary,
                        fontSize = 12.sp,
                        color = if (entry.mine) Color(0xCCFFFFFF) else Color(0xFF757575),
                    )
                }
                if (!entry.final) {
                    Text(
                        "認識中…",
                        fontSize = 10.sp,
                        color = if (entry.mine) Color(0x99FFFFFF) else Color(0xFF9E9E9E),
                    )
                }
            }
        }
    }
}
