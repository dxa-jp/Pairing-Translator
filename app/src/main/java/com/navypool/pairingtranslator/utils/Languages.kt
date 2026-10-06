package com.navypool.pairingtranslator.utils

data class LanguageInfo(
    val code: String,       // 言語コード (例: "ja")
    val countryCode: String,// 国コード (例: "JP")
    val flagEmoji: String,  // 国旗 (例: "🇯🇵")
    val displayName: String, // 表示テキスト (例: "🇯🇵 JP" または "🇮🇳 IN (bn)")
    val englishName: String // 言語ボタン等で使うフルスペルの言語名 (例: "Japanese")
)

// MultiTranslatorプロジェクトの言語リストを流用
object Languages {
    val list = listOf(
        LanguageInfo("en", "US", "🇺🇸", "🇺🇸 US", "English"),
        LanguageInfo("zh", "CN", "🇨🇳", "🇨🇳 CN", "Chinese"),
        LanguageInfo("fr", "FR", "🇫🇷", "🇫🇷 FR", "French"),
        LanguageInfo("ar", "SA", "🇸🇦", "🇸🇦 SA", "Arabic"),
        LanguageInfo("pt", "PT", "🇵🇹", "🇵🇹 PT", "Portuguese"),
        LanguageInfo("ru", "RU", "🇷🇺", "🇷🇺 RU", "Russian"),
        LanguageInfo("ur", "PK", "🇵🇰", "🇵🇰 PK", "Urdu"),
        LanguageInfo("id", "ID", "🇮🇩", "🇮🇩 ID", "Indonesian"),
        LanguageInfo("de", "DE", "🇩🇪", "🇩🇪 DE", "German"),
        LanguageInfo("sw", "KE", "🇰🇪", "🇰🇪 KE", "Swahili"),
        LanguageInfo("ja", "JP", "🇯🇵", "🇯🇵 JP", "Japanese"),
        LanguageInfo("tr", "TR", "🇹🇷", "🇹🇷 TR", "Turkish"),
        LanguageInfo("vi", "VN", "🇻🇳", "🇻🇳 VN", "Vietnamese"),
        LanguageInfo("tl", "PH", "🇵🇭", "🇵🇭 PH", "Tagalog"),
        LanguageInfo("ko", "KR", "🇰🇷", "🇰🇷 KR", "Korean"),
        LanguageInfo("it", "IT", "🇮🇹", "🇮🇹 IT", "Italian"),
        LanguageInfo("th", "TH", "🇹🇭", "🇹🇭 TH", "Thai"),
        LanguageInfo("fa", "IR", "🇮🇷", "🇮🇷 IR", "Persian"),
        LanguageInfo("pl", "PL", "🇵🇱", "🇵🇱 PL", "Polish"),
        LanguageInfo("uk", "UA", "🇺🇦", "🇺🇦 UA", "Ukrainian"),
        LanguageInfo("ms", "MY", "🇲🇾", "🇲🇾 MY", "Malay"),
        LanguageInfo("ro", "RO", "🇷🇴", "🇷🇴 RO", "Romanian"),
        LanguageInfo("nl", "NL", "🇳🇱", "🇳🇱 NL", "Dutch"),
        LanguageInfo("az", "AZ", "🇦🇿", "🇦🇿 AZ", "Azerbaijani"),
        LanguageInfo("af", "ZA", "🇿🇦", "🇿🇦 ZA", "Afrikaans"),
        LanguageInfo("el", "GR", "🇬🇷", "🇬🇷 GR", "Greek"),
        LanguageInfo("cs", "CZ", "🇨🇿", "🇨🇿 CZ", "Czech"),
        LanguageInfo("sv", "SE", "🇸🇪", "🇸🇪 SE", "Swedish"),
        LanguageInfo("hu", "HU", "🇭🇺", "🇭🇺 HU", "Hungarian"),
        LanguageInfo("kk", "KZ", "🇰🇿", "🇰🇿 KZ", "Kazakh"),
        LanguageInfo("sr", "RS", "🇷🇸", "🇷🇸 RS", "Serbian"),
        LanguageInfo("bg", "BG", "🇧🇬", "🇧🇬 BG", "Bulgarian"),
        LanguageInfo("he", "IL", "🇮🇱", "🇮🇱 IL", "Hebrew"),
        LanguageInfo("be", "BY", "🇧🇾", "🇧🇾 BY", "Belarusian"),
        LanguageInfo("hr", "HR", "🇭🇷", "🇭🇷 HR", "Croatian"),
        LanguageInfo("bs", "BA", "🇧🇦", "🇧🇦 BA", "Bosnian"),
        LanguageInfo("sq", "AL", "🇦🇱", "🇦🇱 AL", "Albanian"),
        LanguageInfo("fi", "FI", "🇫🇮", "🇫🇮 FI", "Finnish"),
        LanguageInfo("sk", "SK", "🇸🇰", "🇸🇰 SK", "Slovak"),
        LanguageInfo("da", "DK", "🇩🇰", "🇩🇰 DK", "Danish"),
        LanguageInfo("no", "NO", "🇳🇴", "🇳🇴 NO", "Norwegian"),
        LanguageInfo("lt", "LT", "🇱🇹", "🇱🇹 LT", "Lithuanian"),
        LanguageInfo("sl", "SI", "🇸🇮", "🇸🇮 SI", "Slovenian"),
        LanguageInfo("mk", "MK", "🇲🇰", "🇲🇰 MK", "Macedonian"),
        LanguageInfo("lv", "LV", "🇱🇻", "🇱🇻 LV", "Latvian"),
        LanguageInfo("et", "EE", "🇪🇪", "🇪🇪 EE", "Estonian"),
        LanguageInfo("cy", "GB", "🇬🇧", "🇬🇧 GB", "Welsh"),
        LanguageInfo("hi", "IN", "🇮🇳", "🇮🇳 IN", "Hindi"),
        LanguageInfo("bn", "IN", "🇮🇳", "🇮🇳 IN (bn)", "Bengali"),
        LanguageInfo("pa", "IN", "🇮🇳", "🇮🇳 IN (pa)", "Punjabi"),
        LanguageInfo("mr", "IN", "🇮🇳", "🇮🇳 IN (mr)", "Marathi"),
        LanguageInfo("te", "IN", "🇮🇳", "🇮🇳 IN (te)", "Telugu"),
        LanguageInfo("ta", "IN", "🇮🇳", "🇮🇳 IN (ta)", "Tamil"),
        LanguageInfo("gu", "IN", "🇮🇳", "🇮🇳 IN (gu)", "Gujarati"),
        LanguageInfo("kn", "IN", "🇮🇳", "🇮🇳 IN (kn)", "Kannada"),
        LanguageInfo("ml", "IN", "🇮🇳", "🇮🇳 IN (ml)", "Malayalam"),
        LanguageInfo("es", "ES", "🇪🇸", "🇪🇸 ES", "Spanish"),
        LanguageInfo("ca", "ES", "🇪🇸", "🇪🇸 ES (ca)", "Catalan"),
        LanguageInfo("gl", "ES", "🇪🇸", "🇪🇸 ES (gl)", "Galician"),
        LanguageInfo("eu", "ES", "🇪🇸", "🇪🇸 ES (eu)", "Basque")
    )

    val codeToDisplayMap = list.associate { it.code to it.displayName }
    val codeMap = list.associateBy { it.code }

    fun isValidCode(code: String): Boolean = codeMap.containsKey(code)

    // 言語コード(または旧形式の名称)から正規の言語コードを取得する
    fun getLanguageCode(input: String): String {
        if (codeMap.containsKey(input)) return input
        val match = list.firstOrNull {
            it.code.equals(input, ignoreCase = true) || it.displayName == input ||
                it.englishName.equals(input, ignoreCase = true)
        }
        if (match != null) return match.code
        // 端末locale由来の "ja-JP" 形式に対応
        val prefix = input.substringBefore('-').substringBefore('_').lowercase()
        if (codeMap.containsKey(prefix)) return prefix
        return "ja" // fallback
    }
}
