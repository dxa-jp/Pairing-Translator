package com.navypool.pairingtranslator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navypool.pairingtranslator.connection.PeerLinkManager
import com.navypool.pairingtranslator.connection.SpeechEntry
import com.navypool.pairingtranslator.utils.Languages
import com.navypool.pairingtranslator.voice.VoiceState

// HS(翻訳サポ)準拠のカラー/サイズ
private val HsBackground = Color(0xFFE8F0E8)
private val HsCardWhite = Color(0xD9FFFFFF)
private val HsPillWhite = Color(0xB8FFFFFF)
private val HsTextMain = Color(0xFF1A1A1A)
private val HsTextSub = Color(0xFF5F6368)
private val HsMineBubble = Color(0xFF16302B)
private val HsMineText = Color(0xFFEAFBF5)
private val HsPeerBubble = Color(0xFFFFF4B0)
private val HsPeerText = Color(0xFF0B2A6B)

/**
 * メイン画面: 下部ツールバー(探索ON/OFF・自分の言語・設定) + チャット形式の会話ビュー。
 * 接続が確立すると翻訳は自動で開始されるため、翻訳開始ボタンは存在しない。
 */
@Composable
fun ConversationScreen(
    peerLink: PeerLinkManager,
    onEnableBluetooth: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(peerLink.entries.size) {
        if (peerLink.entries.isNotEmpty()) {
            listState.animateScrollToItem(peerLink.entries.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ステータスバー・ナビゲーションバー・カットアウトと重ならないようにする
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .background(HsBackground),
    ) {
        Column(Modifier.fillMaxSize()) {
            StatusHeader(peerLink)
            LogViewer(peerLink.logs)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(peerLink.entries) { entry ->
                    SpeechBubble(entry, peerLang = peerLink.peer?.lang)
                }
            }
        }

        ToolbarCard(
            peerLink = peerLink,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 24.dp),
        )
    }
}

/** HSのツールバーCard(80dp・角丸40)をComposeで再現したもの。Cardの色が状態を示す */
@Composable
private fun ToolbarCard(
    peerLink: PeerLinkManager,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // HSのステータス色: 接続済み=シアン/探索中=淡グレー/停止・エラー=赤
    val cardColor = when {
        !peerLink.started -> Color(0xFFB23A18)
        peerLink.voiceState == VoiceState.ERROR -> Color(0xFFB23A18)
        peerLink.state == PeerLinkManager.State.CONNECTED -> Color(0xFF80DEEA)
        else -> Color(0xFFF1F5F9)
    }
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(40.dp),
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color(0x4DFFFFFF)),
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SearchToggleButton(peerLink)
            LanguagePill(peerLink)
            CircleIconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "設定",
                    tint = HsTextSub,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** 相手探索のON/OFF(状態表示兼用)。HSの翻訳開始ボタンと同じ位置(ツールバー左端)に置くピル */
@Composable
private fun SearchToggleButton(peerLink: PeerLinkManager) {
    val label = when {
        !peerLink.started -> "探索 OFF"
        peerLink.voiceState == VoiceState.ERROR -> "音声エラー"
        peerLink.state == PeerLinkManager.State.CONNECTED -> "接続済み"
        else -> "探索中…"
    }
    // NOTE: この端末(CP08_J1)ではModifier.shadow()が白い八角形として
    // 描画されるため、ツールバー上のボタンには影を付けない。
    // Card本体の影(shadowElevation=12)は正常に描画される。
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(HsPillWhite)
            .clickable { if (peerLink.started) peerLink.stop() else peerLink.start() },
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .height(49.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = HsTextMain,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HsTextMain)
        }
    }
}

/** 自分の入力言語ピル(旗20sp+コード15sp bold)。タップでドロップダウン選択 */
@Composable
private fun LanguagePill(peerLink: PeerLinkManager) {
    var expanded by remember { mutableStateOf(false) }
    val info = Languages.list.firstOrNull { it.code == peerLink.myLang }

    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .background(HsPillWhite)
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .height(49.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(info?.flagEmoji ?: "🌐", fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    info?.code?.uppercase() ?: peerLink.myLang,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = HsTextMain,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Languages.list.forEach { item ->
                DropdownMenuItem(
                    text = { Text("${item.flagEmoji} ${item.displayName}", fontSize = 14.sp) },
                    onClick = {
                        peerLink.myLang = item.code
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // CircleShapeは一部端末で影が八角形ポリゴンとして描画されるため、
    // 他のピルと同じRoundedCornerShape(100)を使う(49dp正方形なので見た目は円)
    // TEMP検証中: shadowを一時的に無効化して比較する
    Box(
        modifier = modifier
            .size(49.dp)
            // .shadow(6.dp, RoundedCornerShape(100))
            .clip(RoundedCornerShape(100))
            .background(HsPillWhite)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
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

    Surface(color = Color(0xCCF1F5F9)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    color = HsTextSub,
                )
            }
            Text(
                pairText,
                fontSize = 12.sp,
                color = HsTextMain,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SpeechBubble(entry: SpeechEntry, peerLang: String?) {
    if (entry.isSystem) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                entry.original,
                fontSize = 14.sp,
                color = HsTextSub,
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
            color = if (entry.mine) HsMineBubble else HsPeerBubble,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (entry.mine) 12.dp else 2.dp,
                bottomEnd = if (entry.mine) 2.dp else 12.dp,
            ),
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    primary.ifEmpty { "…" },
                    fontSize = 32.sp,
                    color = if (entry.mine) HsMineText else HsPeerText,
                )
                if (secondary.isNotEmpty()) {
                    Text(
                        secondary,
                        fontSize = 24.sp,
                        color = if (entry.mine) Color(0x99EAFBF5) else Color(0x990B2A6B),
                    )
                }
                if (!entry.final) {
                    Text(
                        "認識中…",
                        fontSize = 14.sp,
                        color = if (entry.mine) Color(0x80EAFBF5) else Color(0x800B2A6B),
                    )
                }
            }
        }
    }
}
