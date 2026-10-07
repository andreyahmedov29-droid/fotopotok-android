package com.fotopotok.app.ui

import com.fotopotok.app.api.Photo

sealed class FeedItem {
    data class Single(val photo: Photo) : FeedItem()
    data class Group(val photos: List<Photo>) : FeedItem()
}

/**
 * Flattens the photos into the tiles shown on the main feed — the same layout the
 * web version uses: photos added in one batch collapse into a single "3 in 1" group
 * card, while in select mode every photo is shown separately.
 */
fun buildTiles(photos: List<Photo>, selectMode: Boolean): List<FeedItem> {
    if (selectMode) return photos.map { FeedItem.Single(it) }
    val groupMap = LinkedHashMap<String, MutableList<Photo>>()
    for (p in photos) {
        if (!p.group.isNullOrBlank()) groupMap.getOrPut(p.group!!) { mutableListOf() }.add(p)
    }
    val handled = HashSet<String>()
    val out = mutableListOf<FeedItem>()
    for (p in photos) {
        val g = p.group
        if (!g.isNullOrBlank()) {
            if (!handled.add(g)) continue
            out.add(FeedItem.Group(groupMap[g]!!.sortedBy { it.createdAt }))
        } else {
            out.add(FeedItem.Single(p))
        }
    }
    return out
}
