package com.example.droidautoconnection.utils

data class LanguageInfo(
    val code: String,       // 言語コード (例: "ja")
    val countryCode: String,// 国コード (例: "JP")
    val flagEmoji: String,  // 国旗 (例: "🇯🇵")
    val displayName: String // 表示テキスト (例: "🇯🇵 JP")
)

// MultiTranslatorプロジェクトの言語リストを流用
object Languages {
    val list = listOf(
        LanguageInfo("en", "US", "🇺🇸", "🇺🇸 US"),
        LanguageInfo("zh", "CN", "🇨🇳", "🇨🇳 CN"),
        LanguageInfo("fr", "FR", "🇫🇷", "🇫🇷 FR"),
        LanguageInfo("ar", "SA", "🇸🇦", "🇸🇦 SA"),
        LanguageInfo("pt", "PT", "🇵🇹", "🇵🇹 PT"),
        LanguageInfo("ru", "RU", "🇷🇺", "🇷🇺 RU"),
        LanguageInfo("ur", "PK", "🇵🇰", "🇵🇰 PK"),
        LanguageInfo("id", "ID", "🇮🇩", "🇮🇩 ID"),
        LanguageInfo("de", "DE", "🇩🇪", "🇩🇪 DE"),
        LanguageInfo("sw", "KE", "🇰🇪", "🇰🇪 KE"),
        LanguageInfo("ja", "JP", "🇯🇵", "🇯🇵 JP"),
        LanguageInfo("tr", "TR", "🇹🇷", "🇹🇷 TR"),
        LanguageInfo("vi", "VN", "🇻🇳", "🇻🇳 VN"),
        LanguageInfo("tl", "PH", "🇵🇭", "🇵🇭 PH"),
        LanguageInfo("ko", "KR", "🇰🇷", "🇰🇷 KR"),
        LanguageInfo("it", "IT", "🇮🇹", "🇮🇹 IT"),
        LanguageInfo("th", "TH", "🇹🇭", "🇹🇭 TH"),
        LanguageInfo("fa", "IR", "🇮🇷", "🇮🇷 IR"),
        LanguageInfo("pl", "PL", "🇵🇱", "🇵🇱 PL"),
        LanguageInfo("uk", "UA", "🇺🇦", "🇺🇦 UA"),
        LanguageInfo("ms", "MY", "🇲🇾", "🇲🇾 MY"),
        LanguageInfo("ro", "RO", "🇷🇴", "🇷🇴 RO"),
        LanguageInfo("nl", "NL", "🇳🇱", "🇳🇱 NL"),
        LanguageInfo("az", "AZ", "🇦🇿", "🇦🇿 AZ"),
        LanguageInfo("af", "ZA", "🇿🇦", "🇿🇦 ZA"),
        LanguageInfo("el", "GR", "🇬🇷", "🇬🇷 GR"),
        LanguageInfo("cs", "CZ", "🇨🇿", "🇨🇿 CZ"),
        LanguageInfo("sv", "SE", "🇸🇪", "🇸🇪 SE"),
        LanguageInfo("hu", "HU", "🇭🇺", "🇭🇺 HU"),
        LanguageInfo("kk", "KZ", "🇰🇿", "🇰🇿 KZ"),
        LanguageInfo("sr", "RS", "🇷🇸", "🇷🇸 RS"),
        LanguageInfo("bg", "BG", "🇧🇬", "🇧🇬 BG"),
        LanguageInfo("he", "IL", "🇮🇱", "🇮🇱 IL"),
        LanguageInfo("be", "BY", "🇧🇾", "🇧🇾 BY"),
        LanguageInfo("hr", "HR", "🇭🇷", "🇭🇷 HR"),
        LanguageInfo("bs", "BA", "🇧🇦", "🇧🇦 BA"),
        LanguageInfo("sq", "AL", "🇦🇱", "🇦🇱 AL"),
        LanguageInfo("fi", "FI", "🇫🇮", "🇫🇮 FI"),
        LanguageInfo("sk", "SK", "🇸🇰", "🇸🇰 SK"),
        LanguageInfo("da", "DK", "🇩🇰", "🇩🇰 DK"),
        LanguageInfo("no", "NO", "🇳🇴", "🇳🇴 NO"),
        LanguageInfo("lt", "LT", "🇱🇹", "🇱🇹 LT"),
        LanguageInfo("sl", "SI", "🇸🇮", "🇸🇮 SI"),
        LanguageInfo("mk", "MK", "🇲🇰", "🇲🇰 MK"),
        LanguageInfo("lv", "LV", "🇱🇻", "🇱🇻 LV"),
        LanguageInfo("et", "EE", "🇪🇪", "🇪🇪 EE"),
        LanguageInfo("cy", "GB", "🇬🇧", "🇬🇧 GB"),
        LanguageInfo("hi", "IN", "🇮🇳", "🇮🇳 IN"),
        LanguageInfo("bn", "IN", "🇮🇳", "🇮🇳 IN (bn)"),
        LanguageInfo("pa", "IN", "🇮🇳", "🇮🇳 IN (pa)"),
        LanguageInfo("mr", "IN", "🇮🇳", "🇮🇳 IN (mr)"),
        LanguageInfo("te", "IN", "🇮🇳", "🇮🇳 IN (te)"),
        LanguageInfo("ta", "IN", "🇮🇳", "🇮🇳 IN (ta)"),
        LanguageInfo("gu", "IN", "🇮🇳", "🇮🇳 IN (gu)"),
        LanguageInfo("kn", "IN", "🇮🇳", "🇮🇳 IN (kn)"),
        LanguageInfo("ml", "IN", "🇮🇳", "🇮🇳 IN (ml)"),
        LanguageInfo("es", "ES", "🇪🇸", "🇪🇸 ES"),
        LanguageInfo("ca", "ES", "🇪🇸", "🇪🇸 ES (ca)"),
        LanguageInfo("gl", "ES", "🇪🇸", "🇪🇸 ES (gl)"),
        LanguageInfo("eu", "ES", "🇪🇸", "🇪🇸 ES (eu)")
    )

    val codeToDisplayMap = list.associate { it.code to it.displayName }
    val codeMap = list.associateBy { it.code }

    fun isValidCode(code: String): Boolean = codeMap.containsKey(code)

    // 言語コード(または旧形式の名称)から正規の言語コードを取得する
    fun getLanguageCode(input: String): String {
        if (codeMap.containsKey(input)) return input
        val match = list.firstOrNull {
            it.code.equals(input, ignoreCase = true) || it.displayName == input
        }
        if (match != null) return match.code
        // 端末locale由来の "ja-JP" 形式に対応
        val prefix = input.substringBefore('-').substringBefore('_').lowercase()
        if (codeMap.containsKey(prefix)) return prefix
        return "ja" // fallback
    }
}
