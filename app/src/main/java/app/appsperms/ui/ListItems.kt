package app.appsperms.ui

import app.appsperms.model.AppEntry

/** Isi RecyclerView: pemisah section + baris app. Key includes userId to support clones. */
sealed interface ListItem {
    val key: String

    data class Header(val title: String, val count: Int) : ListItem {
        override val key: String get() = "header:$title"
    }

    data class App(val entry: AppEntry) : ListItem {
        override val key: String get() = "app:${entry.packageName}:u${entry.userId}:uid${entry.uid}"
    }
}
