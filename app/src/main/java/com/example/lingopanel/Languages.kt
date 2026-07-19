package com.example.lingopanel

data class Lang(val code: String, val label: String)

object Languages {
    val ALL = listOf(
        Lang("id", "Indonesia"),
        Lang("en", "Inggris"),
        Lang("ja", "Jepang"),
        Lang("ko", "Korea"),
        Lang("zh", "Mandarin"),
        Lang("es", "Spanyol"),
        Lang("fr", "Prancis"),
        Lang("ar", "Arab"),
        Lang("de", "Jerman"),
        Lang("it", "Italia"),
        Lang("pt", "Portugis"),
        Lang("ru", "Rusia"),
        Lang("nl", "Belanda"),
        Lang("vi", "Vietnam"),
        Lang("th", "Thailand"),
        Lang("hi", "Hindi"),
        Lang("tr", "Turki"),
        Lang("ms", "Melayu"),
        Lang("tl", "Filipino"),
        Lang("bn", "Bengali"),
        Lang("pl", "Polandia"),
        Lang("sv", "Swedia"),
        Lang("uk", "Ukraina"),
        Lang("el", "Yunani"),
        Lang("he", "Ibrani"),
        Lang("fa", "Persia"),
        Lang("ur", "Urdu"),
        Lang("ta", "Tamil"),
        Lang("km", "Khmer"),
        Lang("my", "Burma")
    )

    val SOURCE_OPTIONS = ALL
    val TARGET_OPTIONS = ALL
}
