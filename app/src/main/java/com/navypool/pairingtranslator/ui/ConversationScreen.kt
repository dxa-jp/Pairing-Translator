package com.navypool.pairingtranslator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navypool.pairingtranslator.R
import com.navypool.pairingtranslator.connection.PeerLinkManager
import com.navypool.pairingtranslator.connection.SpeechEntry
import com.navypool.pairingtranslator.utils.Languages
import com.navypool.pairingtranslator.voice.VoiceState

// HS(翻訳サポ)準拠のカラー/サイズ
private val HsBackground = Color(0xFFE8F0E8)
private val HsCardWhite = Color(0xD9FFFFFF)
private val HsTextMain = Color(0xFF1A1A1A)
private val HsTextSub = Color(0xFF5F6368)
private val HsMineBubble = Color(0xFF16302B)
private val HsMineText = Color(0xFFEAFBF5)
private val HsPeerBubble = Color(0xFFFFF4B0)
private val HsPeerText = Color(0xFF0B2A6B)

// ボタンの擬似エレベーション: 本端末のGPUではModifier.shadow()が白い八角形に
// 化けるため、白→薄灰のグラデーション+細い境界線+手動の影で立体感を出す
private val HsPillBrush = Brush.verticalGradient(listOf(Color(0xF0FFFFFF), Color(0xFFE7EDF5)))
private val HsPillBorder = Color(0x335F6368)
private val HsFakeShadow = Color(0x26000000)

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

    var debugVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ステータスバー・ナビゲーションバー・カットアウトと重ならないようにする
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .background(HsBackground),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = if (debugVisible) 380.dp else 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(peerLink.entries) { entry ->
                SpeechBubble(entry, peerLang = peerLink.peer?.lang)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            AnimatedVisibility(
                visible = debugVisible,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                DebugLogPanel(
                    logs = peerLink.logs,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            ToolbarCard(
                peerLink = peerLink,
                onOpenSettings = onOpenSettings,
                debugVisible = debugVisible,
                onToggleDebug = { debugVisible = !debugVisible },
            )
        }
    }
}

/** HSのツールバーCard(80dp・角丸40)をComposeで再現したもの。Cardの色が状態を示す */
@Composable
private fun ToolbarCard(
    peerLink: PeerLinkManager,
    onOpenSettings: () -> Unit,
    debugVisible: Boolean,
    onToggleDebug: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchToggleButton(peerLink)
                Spacer(Modifier.width(12.dp))
                DebugToggleButton(visible = debugVisible, onToggle = onToggleDebug)
            }
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

/** デバッグログウィンドウの開閉ボタン(HSのbtnDebugLogToggle相当) */
@Composable
private fun DebugToggleButton(visible: Boolean, onToggle: () -> Unit) {
    Box(Modifier.size(49.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 2.dp)
                .background(HsFakeShadow, RoundedCornerShape(100)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(HsPillBrush, RoundedCornerShape(100))
                .border(1.dp, HsPillBorder, RoundedCornerShape(100))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bug_custom),
                contentDescription = "デバッグログ",
                tint = if (visible) Color(0xFF388E3C) else HsTextSub,
                modifier = Modifier.size(24.dp),
            )
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
    // 描画されるため、代わりに半透明黒の「疑似影」とグラデーションで
    // 立体感を出す。Card本体の影(shadowElevation=12)は正常に描画される。
    Box {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 2.dp)
                .background(HsFakeShadow, RoundedCornerShape(100)),
        )
        Row(
            modifier = Modifier
                .background(HsPillBrush, RoundedCornerShape(100))
                .border(1.dp, HsPillBorder, RoundedCornerShape(100))
                .clickable { if (peerLink.started) peerLink.stop() else peerLink.start() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
            Modifier
                .matchParentSize()
                .offset(y = 2.dp)
                .background(HsFakeShadow, RoundedCornerShape(100)),
        )
        Row(
            modifier = Modifier
                .background(HsPillBrush, RoundedCornerShape(100))
                .border(1.dp, HsPillBorder, RoundedCornerShape(100))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Languages.list.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayName, fontSize = 14.sp) },
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
    Box(modifier.size(49.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 2.dp)
                .background(HsFakeShadow, RoundedCornerShape(100)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(HsPillBrush, RoundedCornerShape(100))
                .border(1.dp, HsPillBorder, RoundedCornerShape(100))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
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
