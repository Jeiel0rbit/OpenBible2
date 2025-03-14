package com.schwegelbin.openbible.logic

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

enum class SelectMode {
    Translation, Book, Chapter
}

enum class ThemeOption {
    System, Light, Dark, Amoled
}

enum class SchemeOption {
    Dynamic, Static
}

enum class ReadTextAlignment {
    Start, Justify
}

fun saveIndex(context: Context) {
    downloadFile(
        context = context,
        url = "https://api.getbible.net/v2/translations.json",
        name = "translations.json",
        relPath = "Index"
    )
}

fun saveChecksum(context: Context) {
    downloadFile(
        context = context,
        url = "https://api.getbible.net/v2/checksum.json",
        name = "checksum.json",
        relPath = "Index"
    )
}

fun downloadTranslation(context: Context, abbrev: String) {
    downloadFile(
        context = context,
        url = "https://api.getbible.net/v2/${abbrev}.json",
        name = "${abbrev}.json",
        relPath = "Translations"
    )
    File(
        "${context.getExternalFilesDir("Checksums")}/${abbrev}"
    ).writeText(getChecksum(context, abbrev))
}

fun checkUpdate(context: Context, abbrev: String): Boolean {
    if (!File(
            "${context.getExternalFilesDir("Translations")}/${abbrev}.json"
        ).exists()
    ) return true
    val path = "${context.getExternalFilesDir("Checksums")}/${abbrev}"
    if (!File(path).exists()) return true
    val latest = getChecksum(context, abbrev)
    val current = File(path).readText()
    return latest != current
}

fun downloadFile(
    context: Context, url: String, name: String, relPath: String = "", replace: Boolean = true
): Long {
    if (replace) File("${context.getExternalFilesDir(relPath)}/${name}").delete()
    val notify =
        if (getDownloadNotification(context)) DownloadManager.Request.VISIBILITY_VISIBLE
        else DownloadManager.Request.VISIBILITY_HIDDEN
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setTitle("Downloading $name")
        setDescription("Downloading $name")
        setNotificationVisibility(notify)
        setDestinationInExternalFilesDir(context, relPath, name)
    }
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return downloadManager.enqueue(request)
}

fun saveSelection(
    context: Context,
    translation: String? = null,
    book: Int? = null,
    chapter: Int? = null,
    isSplitScreen: Boolean
) {
    val editor = context.getSharedPreferences("selection", Context.MODE_PRIVATE).edit()
    if (!isSplitScreen) {
        if (translation != null) editor.putString("translation", translation)
        if (book != null) editor.putInt("book", book)
        if (chapter != null) editor.putInt("chapter", chapter)
    } else {
        if (translation != null) editor.putString("translation_split", translation)
        if (book != null) editor.putInt("book_split", book)
        if (chapter != null) editor.putInt("chapter_split", chapter)
    }
    editor.apply()
}

fun saveColorScheme(
    context: Context,
    theme: ThemeOption? = null,
    scheme: SchemeOption? = null
) {
    val editor = context.getSharedPreferences("options", Context.MODE_PRIVATE).edit()
    if (theme != null) editor.putString("theme", theme.toString())
    if (scheme != null) editor.putString("scheme", scheme.toString())
    editor.apply()
}

fun saveTextStyle(context: Context, alignment: ReadTextAlignment) {
    context.getSharedPreferences("options", Context.MODE_PRIVATE).edit()
        .putString("textAlignment", alignment.toString()).apply()
}

fun saveShowVerseNumbers(context: Context, shown: Boolean) {
    context.getSharedPreferences("options", Context.MODE_PRIVATE).edit()
        .putBoolean("showVerseNumbers", shown).apply()
}

fun saveCheckAtStartup(context: Context, check: Boolean) {
    context.getSharedPreferences("options", Context.MODE_PRIVATE).edit()
        .putBoolean("checkAtStartup", check).apply()
}

fun saveSplitScreen(context: Context, enabled: Boolean) {
    context.getSharedPreferences("options", Context.MODE_PRIVATE).edit()
        .putBoolean("splitScreen", enabled).apply()
}

fun saveDownloadNotification(context: Context, enabled: Boolean) {
    context.getSharedPreferences("options", Context.MODE_PRIVATE).edit()
        .putBoolean("notifyDownload", enabled).apply()
}

fun saveNewIndex(context: Context) {
    val path = context.getExternalFilesDir("Index")
    val translationsFile = File("${path}/translations.json")
    val checksumFile = File("${path}/checksum.json")
    val currentTime = System.currentTimeMillis()
    val dayTime = 86_400_000L
    if (
        !translationsFile.exists() ||
        !checksumFile.exists() ||
        currentTime - translationsFile.lastModified() > dayTime ||
        currentTime - checksumFile.lastModified() > dayTime
    ) {
        saveIndex(context)
        saveChecksum(context)
    }
}

fun checkForUpdates(context: Context, update: Boolean): Boolean {
    var updateAvailable = false
    cleanUpTranslations(context)
    getList(context, "Checksums").map { it.name }.forEach { abbrev ->
        if (checkUpdate(context, abbrev)) {
            if (update) downloadTranslation(context, abbrev)
            updateAvailable = true
        }
    }
    return updateAvailable
}

fun cleanUpTranslations(context: Context) {
    val checksums = getList(context, "Checksums").map { it.name }
    val translations = getList(context, "Translations").map { it.nameWithoutExtension }
    checksums.forEach { sum ->
        if (sum !in translations) File("${context.getExternalFilesDir("Checksums")}/${sum}").delete()
    }
    translations.forEach { abbrev ->
        if (abbrev !in checksums) File("${context.getExternalFilesDir("Checksums")}/${abbrev}").writeText(
            "unknown"
        )
    }
}

fun checkTranslation(
    context: Context,
    abbrev: String,
    onNavigateToStart: () -> Unit,
    isSplitScreen: Boolean
): String {
    if (!File("${context.getExternalFilesDir("Translations")}/${abbrev}.json").exists()) {
        val list = getList(context, "Translations").map { it.nameWithoutExtension }
        if (list.isNotEmpty()) {
            val newTranslation = list.first()
            saveSelection(context, newTranslation, isSplitScreen = isSplitScreen)
            return newTranslation
        } else onNavigateToStart()
    }
    return abbrev
}

fun shorten(str: String, max: Int): String {
    return if (max >= 2 && str.length > max)
        str.substring(0, max - 1).trim() + '.'
    else str
}