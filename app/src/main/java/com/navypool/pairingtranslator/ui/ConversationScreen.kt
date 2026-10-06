package com.navypool.pairingtranslator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navypool.pairingtranslator.R
import com.navypool.pairingtranslator.connection.PeerLinkManager
import com.navypool.pairingtranslator.connection.SpeechEntry
import com.navypool.pairingtranslator.connection.TranslationPhase
import com.navypool.pairingtranslator.utils.Languages

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
 * メイン画面: 左カラム(25%)は自端末の状態色で塗られ、3ブロック構成。
 * 上揃えブロック: 人アイコン+「You」。
 * 中央ブロック: 言語ボタン(フルスペル)→「Select your language」→翻訳ボタン(英語表記)。
 *   2つのボタンはアイコン/テキスト200%。
 * 下揃えブロック: 丸いアイコンボタン3つ(設定/デバッグ/ペアリング開始停止)。
 * 右カラム(75%)はチャット形式の会話ビューで、新しい発言が上・自分の発話が左に表示される。
 * ペアリング済みでオフラインのときは右カラムが薄い赤になる。
 */
@Composable
fun ConversationScreen(
    peerLink: PeerLinkManager,
    onEnableBluetooth: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberLazyListState()

    // 新しい発言が上に表示されるため、追加されたら先頭へスクロールする
    LaunchedEffect(peerLink.entries.size) {
        if (peerLink.entries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    var debugVisible by remember { mutableStateOf(false) }

    // 左カラムの状態色: 赤=未ペアリング / グレー=ペアリング済み・翻訳停止中 / 青=ペアリング済み・翻訳中
    val paired = peerLink.peer != null
    val navbarColor = when {
        !paired -> Color(0xFFB23A18)
        peerLink.translationPhase == TranslationPhase.RUNNING -> Color(0xFF4E86C6)
        else -> Color(0xFF9CA3AF)
    }
    // 右カラム: ペアリング済みでインターネットがオフラインのときだけ薄い赤で警告する
    val rightBackground = if (paired && !peerLink.online) Color(0xFFF2C4C0) else HsBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ステータスバー・ナビゲーションバー・カットアウトと重ならないようにする
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .background(HsBackground),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左カラム(25%): カラム自体を状態色で塗り、上33%に「You」、下67%に旧下部ツールバーを縦積みで配置
            Column(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .background(navbarColor)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // タイト版アイコンの50%サイズ
                    val iconSize = minOf(120.dp, (maxHeight - 390.dp) / 2)
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 上揃えブロック: 人アイコン+You。
                        // アイコンは余白を切り詰めた専用ベクターで描画(Icons.Filled.Personは
                        // グリフ自体に上下17%ずつの余白があり大きく見える隙間の原因になる)
                        Spacer(Modifier.height(24.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_person_tight),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(iconSize),
                        )
                        Text(
                            "You",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        // 中央ブロック: 残り余白を前後のSpacerで均等に分け、
                        // 上ブロックと下ブロックの間に等間隔で置く
                        Spacer(Modifier.weight(1f))
                        LanguagePill(peerLink, Modifier.fillMaxWidth(0.8f))
                        Text(
                            "Select your language",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        // ラベルと翻訳ボタンの間は少し広めに空ける
                        Spacer(Modifier.height(24.dp))
                        SonioxButton(peerLink, Modifier.fillMaxWidth(0.8f))
                        Spacer(Modifier.weight(1f))
                        NavbarButtons(
                            peerLink = peerLink,
                            debugVisible = debugVisible,
                            onToggleDebug = { debugVisible = !debugVisible },
                            onOpenSettings = onOpenSettings,
                        )
                    }
                }
            }

            // 右カラム(75%): 翻訳表示エリア。ペアリング済みでオフラインのときは薄い赤。
            // デバッグログはここにオーバーレイする
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxHeight()
                    .background(rightBackground),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(peerLink.entries.asReversed(), key = { it.id }) { entry ->
                        SpeechBubble(entry, peerLang = peerLink.peer?.lang)
                    }
                }
                // Rowスコープ直下ではないため、スコープ付きオーバーロードを避けて完全修飾名で呼ぶ
                androidx.compose.animation.AnimatedVisibility(
                    visible = debugVisible,
                    enter = expandVertically(expandFrom = Alignment.Bottom),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    DebugLogPanel(logs = peerLink.logs)
                }
            }
        }
    }
}

/**
 * Sonioxへの接続ボタン(MultiTranslatorのbtnAction相当)。タップで両端末の翻訳を開始/停止する。
 * 表記はMultiTranslatorに合わせ英語(READY/LISTENING)。CONTINUE状態はなく、
 * 一時キーは失効前に自動更新される。
 * キー取得〜Soniox接続〜相手の応答待ち(STARTING)の間は無効化して再タップを防ぐ。
 */
@Composable
private fun SonioxButton(peerLink: PeerLinkManager, modifier: Modifier = Modifier) {
    val phase = peerLink.translationPhase
    val enabled = peerLink.peer != null && phase != TranslationPhase.STARTING
    // MultiTranslatorと同じ英語表記。開始処理中もREADYのまま無効化する(MTと同挙動)
    val label = if (phase == TranslationPhase.RUNNING) "LISTENING" else "READY"
    val tint = if (enabled) HsTextMain else HsTextSub
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 2.dp)
                .background(HsFakeShadow, RoundedCornerShape(20.dp)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp) // 指定の200%高
                .alpha(if (enabled) 1f else 0.55f)
                .background(HsPillBrush, RoundedCornerShape(20.dp))
                .border(1.dp, HsPillBorder, RoundedCornerShape(20.dp))
                .clickable(enabled = enabled) { peerLink.toggleTranslation() }
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (phase == TranslationPhase.RUNNING) R.drawable.ic_ear_custom else R.drawable.ic_mic_custom,
                ),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(40.dp),
            )
            // ラベルはアイコンの下に、以前のサイズ(28spの50%)で表示する
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

/** 翻訳開始/停止ボタンの下に続く丸いアイコンボタン3つ(設定・デバッグログ・ペアリング開始/停止) */
@Composable
private fun NavbarButtons(
    peerLink: PeerLinkManager,
    debugVisible: Boolean,
    onToggleDebug: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        CircleIconButton(onClick = onOpenSettings, modifier = Modifier.size(49.dp)) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "設定",
                tint = HsTextSub,
                modifier = Modifier.size(24.dp),
            )
        }
        CircleIconButton(onClick = onToggleDebug, modifier = Modifier.size(49.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_bug_custom),
                contentDescription = "デバッグログ",
                tint = if (debugVisible) Color(0xFF388E3C) else HsTextSub,
                modifier = Modifier.size(24.dp),
            )
        }
        CircleIconButton(
            onClick = { if (peerLink.started) peerLink.stop() else peerLink.start() },
            modifier = Modifier.size(49.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "ペアリング(相手探索)の開始/停止",
                // 探索中は濃色、停止中は白っぽくして状態を判別できるようにする
                tint = if (peerLink.started) HsTextMain else Color(0xB3FFFFFF),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 自分の入力言語ピル(旗+フルスペル言語名)。タップでドロップダウン選択 */
@Composable
private fun LanguagePill(
    peerLink: PeerLinkManager,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val info = Languages.list.firstOrNull { it.code == peerLink.myLang }

    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 2.dp)
                .background(HsFakeShadow, RoundedCornerShape(20.dp)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp) // 指定の200%高
                .background(HsPillBrush, RoundedCornerShape(20.dp))
                .border(1.dp, HsPillBorder, RoundedCornerShape(20.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(info?.flagEmoji ?: "🌐", fontSize = 40.sp)
            // 言語名は旗の下に、以前のサイズ(30spの50%)で表示する
            Text(
                info?.englishName ?: peerLink.myLang,
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
    Box(modifier.height(49.dp)) {
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

    // 自分の発話は左寄せ、相手の発話は右寄せ(標準チャットアプリと左右逆の配置)。
    // 吹き出しの内容: 自分の発話は原文(自分の言語)が主、訳文が副。
    //                 相手の発話は訳文(自分の言語)が主、原文が副。
    val primary = if (entry.mine) entry.original else entry.translation.ifEmpty { entry.original }
    val secondary = if (entry.mine) entry.translation else entry.original

    // 吹き出しは画面幅の最大8割まで拡大する
    BoxWithConstraints(
        Modifier.fillMaxWidth(),
        contentAlignment = if (entry.mine) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Surface(
            color = if (entry.mine) HsMineBubble else HsPeerBubble,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (entry.mine) 2.dp else 12.dp,
                bottomEnd = if (entry.mine) 12.dp else 2.dp,
            ),
            modifier = Modifier.widthIn(max = maxWidth * 0.8f),
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
