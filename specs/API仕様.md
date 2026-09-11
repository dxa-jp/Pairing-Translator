# API仕様 — ペアリング翻訳

> 対象: D:\DroidAutoConnetionSonix v0.2.1 — 本書が最新のAPI仕様であり、他の世代の仕様書は廃止済み。
> 扱う範囲: ①MultiTranslatorサーバーREST API、②Soniox WebSocket API、③アプリ間P2Pプロトコル

## 1. MultiTranslator サーバー REST API

ベースURL: `https://multitranslator.dev.x-tools.biz/`(form-urlencoded POST、レスポンスJSON)

### 1.1 端末登録 — `POST /api/device_auth.php`

| 項目 | 値 |
|---|---|
| リクエスト | `device_id` (ANDROID_IDベースのUUID、初回起動時に生成してSharedPreferencesに永続化) |
| 成功(承認済み) | `{"success": true, "data": {"device_no": <int>, "status": true}}` |
| 未承認 | `{"success": true, "data": {"device_no": <int>, "status": false}}` |
| 不明デバイス | 403 + error(サーバー側で自動登録されるため通常発生しない) |

- 呼び出しタイミング: アプリ起動時。未承認の場合、画面復帰のたびに再確認する
- 承認は MultiTranslator 管理UIで行う(サーバー仕様参照)

### 1.2 一時キー発行 — `POST /api/temp_key.php`

| 項目 | 値 |
|---|---|
| リクエスト | `device_id`, `duration`(分、現在は "60"。サーバー側は実効1時間で固定発行) |
| 成功 | `{"success": true, "temp_api_key": "snx_tmp_...", "model": "<サーバー設定のモデル名>"}` |
| 拒否 | `error_code`: `DEVICE_NOT_FOUND` / `DEVICE_NOT_APPROVED` / `QUOTA_EXCEEDED` + `message` |

- キーの有効期間: 約1時間。アプリは55分ごとにローテーションする
- 発行履歴はサーバーの `temp_key_history` に記録される

## 2. Soniox WebSocket API(アプリの利用範囲)

エンドポイント: `wss://stt-rt.soniox.com/transcribe-websocket`
モデル: `stt-rt-v5` / 音声: 16kHz PCM s16le モノラル(バイナリフレームで送信)

### 2.1 設定メッセージ(接続直後に1回、JSON)

```json
{
  "api_key": "<一時キー>",
  "model": "<一時キーAPIの戻り値のmodel>",
  "language": "ja",
  "audio_format": "pcm_s16le",
  "sample_rate": 16000,
  "num_channels": 1,
  "enable_streaming_translation": true,
  "include_non_final": true,
  "translation": {
    "type": "one_way",
    "target_language": "en"
  }
}
```

**重要な実装上の約束事**(実機検証で判明):

- 入力言語は**単数形の `"language"` で指定**する(HonyakuSapo実績方式)。
  `"languages"`(複数形配列)を渡すと one_way 翻訳が効かなくなる
- `"language"` を Auto でなく固定指定すると、指定外言語の音声は無視される
  (自端末マイクに混入した相手の声を翻訳しないための仕様活用)

### 2.2 結果メッセージ(JSON)

```json
{
  "tokens": [
    {"text": "今日は", "is_final": false, "translation_status": "original", "language": "ja"},
    {"text": "The weather", "is_final": true, "translation_status": "translation", "language": "en"}
  ]
}
```

- トークン属性: `text` / `is_final` / `translation_status`(`original`=認識, `translation`=訳文) / `language`
- 各メッセージの `tokens` は現在発話の累積。表示には既出プレフィックスの除去が必要

### 2.3 確定ルール(アプリの実装方針)

- **訳文の `is_final` が立った時点で**「ファイナル原文+ファイナル訳文」のペアを確定する
- 原文の `is_final` では確定しない(訳文は原文より遅れて届くため)
- 送信用蓄積は `is_final` トークンのみ(未確定トークンを含めると断片化する)
- エラーは `error_type` / `error_message` で通知され接続が閉じる(429 limit_exceeded / 413 max_duration_reached / 503 service_unavailable 等)。アプリは自動再接続する

## 3. アプリ間P2Pプロトコル(Nearby Connections)

輸送: Nearby Connections BYTESペイロード(上限32KB)、UTF-8 JSON。Service ID: `com.example.droidautoconnection.SONIX_V1`

### 3.1 hello — 接続確立直後に双方向が送信

```json
{"type": "hello", "id": "<deviceId>", "name": "<端末名>", "model": "<型番>", "lang": "ja"}
```

- `lang` が入力言語コード。受信側はこれで音声セッションを開始する

### 3.2 speech — 翻訳確定ペアの送信(確定時のみ、部分送信なし)

```json
{"type": "speech", "id": "<deviceId>", "orig": "<原文>", "trans": "<訳文>", "lang": "ja", "ts": 1699999999999}
```

### 3.3 ping / pong — ハートビート(10秒間隔、25秒無応答で切断扱い)

```json
{"type": "ping", "ts": 1699999999999}
{"type": "pong", "ts": 1699999999999}
```

## 4. 参考実装

- アプリ側クライアント: `app/src/main/java/com/example/droidautoconnection/data/BackendClient.kt`(REST)、`voice/SonioxSocketClient.kt`(WS)
- One-Way翻訳の実績実装: D:\HonyakuSapo `data/SonioxSocketClient.kt`
- two-way翻訳の実績実装: D:\MultiTranslator `data/SonioxSocketClient.kt`

### モデル名の扱い（v0.2.2以降）

一時キー取得の成功応答に含まれる文字列の `model` をSoniox接続時に使用する。
モデル名はアプリ内に固定せず、接続開始・再接続・55分ごとのセッション更新時に一時キーとともに取得する。
`model` が欠落、null、空文字、空白のみ、文字列以外の場合はエラーを表示し、Sonioxへ接続しない。