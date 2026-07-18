package com.example.lingopanel

data class Lang(val code: String, val label: String)

object Languages {
    // "auto" khusus buat opsi Deteksi Otomatis di sisi bahasa sumber
    val ALL = listOf(
        Lang("auto", "Deteksi Otomatis"),
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

    // Sumber: termasuk opsi "auto". Target: tidak termasuk "auto".
    val SOURCE_OPTIONS = ALL
    val TARGET_OPTIONS = ALL.filter { it.code != "auto" }
}
