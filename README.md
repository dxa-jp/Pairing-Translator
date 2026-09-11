# ペアリング翻訳

近くのAndroid端末同士を接続し、Sonioxで音声認識・翻訳を行うアプリです。
端末間通信にはGoogle Play ServicesのNearby Connectionsを使用します。

## ビルド

- Android Studio、JDK 17以上、Android SDK 36
- Android 8.0（API 26）以上のGoogle Play Services搭載端末
- `local.properties` に各環境の `sdk.dir` を設定

```powershell
.\gradlew.bat :app:assembleDebug
```

生成先: `app/build/outputs/apk/debug/app-debug.apk`

## サーバーとモデル設定

既存のMultiTranslatorバックエンドを利用します。端末登録後、管理画面での承認が必要です。
v0.2.2以降は、一時キーAPIの `model` をSoniox接続に使用します。
モデル名をアプリに固定せず、接続開始・再接続・セッション更新時に取得します。
モデル名が未取得・不正な場合はエラーとして停止します。

## 資料

- [アプリ仕様](specs/アプリ仕様.md)
- [API仕様](specs/API仕様.md)
- [サーバー仕様](specs/サーバー仕様.md)
- [実機テスト手順](TESTING.md)

## 検証状況（v0.2.2）

Debug APKのビルドと署名検証済みです。モデル名対応の実機検証は未実施です。
Android Lintでは既存の `InvalidFragmentVersionForActivityResult` エラーが残っています。
