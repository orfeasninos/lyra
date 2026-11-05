package app.lyra.player.widget

import android.content.Context

enum class WidgetTheme(val label: String) {
    LIGHT("Light theme"),
    DARK("Dark theme"),
    SYSTEM("Match with system"),
    ALBUM_ART("Adapt from album art"),
}

/** Οι ρυθμίσεις ενός widget. Κάθε widget στην αρχική οθόνη έχει τις δικές του. */
data class WidgetStyle(
    val theme: WidgetTheme = WidgetTheme.DARK,
    /** 0 = αδιαφανές φόντο, 100 = καθόλου φόντο. */
    val transparency: Int = 5,
    val showArt: Boolean = true,
    val showPlaylist: Boolean = false,
    val showFavorite: Boolean = false,
    val showSeek: Boolean = false,
) {
    /** Τα παραπάνω κουμπιά δεν χωράνε δίπλα στον τίτλο, οπότε μπαίνουν σε δική τους γραμμή. */
    val extended: Boolean get() = showPlaylist || showFavorite || showSeek

    fun save(context: Context, id: Int) {
        prefs(context).edit()
            .putString("${id}_theme", theme.name)
            .putInt("${id}_transparency", transparency)
            .putBoolean("${id}_art", showArt)
            .putBoolean("${id}_playlist", showPlaylist)
            .putBoolean("${id}_favorite", showFavorite)
            .putBoolean("${id}_seek", showSeek)
            .apply()
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("widget_style", Context.MODE_PRIVATE)

        fun load(context: Context, id: Int): WidgetStyle {
            val p = prefs(context)
            val d = WidgetStyle()
            return WidgetStyle(
                theme = WidgetTheme.entries.firstOrNull { it.name == p.getString("${id}_theme", null) } ?: d.theme,
                transparency = p.getInt("${id}_transparency", d.transparency),
                showArt = p.getBoolean("${id}_art", d.showArt),
                showPlaylist = p.getBoolean("${id}_playlist", d.showPlaylist),
                showFavorite = p.getBoolean("${id}_favorite", d.showFavorite),
                showSeek = p.getBoolean("${id}_seek", d.showSeek),
            )
        }

        fun delete(context: Context, id: Int) {
            val p = prefs(context)
            p.edit().apply { p.all.keys.filter { it.startsWith("${id}_") }.forEach(::remove) }.apply()
        }
    }
}
