package app.lyra.player.data

/** Κριτήρια ταξινόμησης άλμπουμ (όπως στο Musicolet). */
enum class AlbumSort(val label: String) {
    NAME("Name"),
    SONGS("Number of songs"),
    DURATION("Duration"),
    YEAR("Year"),
    DATE_UPDATED("Date updated"),
    ARTIST("Artist"),
    ALBUM_ARTIST("Album-Artist"),
    COMPOSER("Composer"),
}

enum class AlbumViewMode(val label: String) {
    LIST("List without thumbnail"),
    LIST_THUMB("List with thumbnail"),
    GRID("Grid view"),
}

/** Ελάχιστο πλάτος κελιού στο πλέγμα: όσο μικρότερο, τόσο περισσότερες στήλες. */
enum class GridSize(val label: String, val minCellDp: Int) {
    SMALLER("Smaller", 60),
    SMALL("Small", 85),
    MEDIUM("Medium", 110),
    BIG("Big", 150),
    BIGGER("Bigger", 200),
}

enum class AlbumExtraInfo(val label: String) {
    AUTO("Decide automatically as per the sorting type"),
    NONE("None"),
    SONGS("Number of songs"),
    DURATION("Duration"),
    DATE_UPDATED("Date updated"),
    YEAR("Year"),
    ARTIST("Artist"),
    ALBUM_ARTIST("Album-Artist"),
    COMPOSER("Composer"),
}

data class AlbumPrefs(
    val sort: AlbumSort = AlbumSort.NAME,
    val descending: Boolean = false,
    val viewMode: AlbumViewMode = AlbumViewMode.GRID,
    val gridSize: GridSize = GridSize.MEDIUM,
    val extraInfo: AlbumExtraInfo = AlbumExtraInfo.AUTO,
    val quickSearch: Boolean = true,
) {
    /** Η πληροφορία που φαίνεται τελικά κάτω από τον τίτλο. */
    fun effectiveExtraInfo(): AlbumExtraInfo = if (extraInfo != AlbumExtraInfo.AUTO) extraInfo else when (sort) {
        // Στη λίστα υπάρχει χώρος, οπότε με ταξινόμηση κατά όνομα δείχνουμε τον καλλιτέχνη
        AlbumSort.NAME -> if (viewMode == AlbumViewMode.GRID) AlbumExtraInfo.NONE else AlbumExtraInfo.ALBUM_ARTIST
        AlbumSort.SONGS -> AlbumExtraInfo.SONGS
        AlbumSort.DURATION -> AlbumExtraInfo.DURATION
        AlbumSort.YEAR -> AlbumExtraInfo.YEAR
        AlbumSort.DATE_UPDATED -> AlbumExtraInfo.DATE_UPDATED
        AlbumSort.ARTIST -> AlbumExtraInfo.ARTIST
        AlbumSort.ALBUM_ARTIST -> AlbumExtraInfo.ALBUM_ARTIST
        AlbumSort.COMPOSER -> AlbumExtraInfo.COMPOSER
    }

    fun comparator(): Comparator<Album> {
        val byTitle = byName<Album> { it.title }
        val primary: Comparator<Album> = when (sort) {
            AlbumSort.NAME -> byTitle
            AlbumSort.SONGS -> compareBy { it.songs.size }
            AlbumSort.DURATION -> compareBy { it.durationMs }
            AlbumSort.YEAR -> compareBy { it.year }
            AlbumSort.DATE_UPDATED -> compareBy { it.dateUpdated }
            AlbumSort.ARTIST -> byName { it.artist }
            AlbumSort.ALBUM_ARTIST -> byName { it.albumArtist ?: it.artistName }
            AlbumSort.COMPOSER -> byName { it.composer.orEmpty() }
        }
        // Οι ισοπαλίες λύνονται πάντα αλφαβητικά, ανεξάρτητα από τη φορά
        return (if (descending) primary.reversed() else primary).then(byTitle)
    }
}
