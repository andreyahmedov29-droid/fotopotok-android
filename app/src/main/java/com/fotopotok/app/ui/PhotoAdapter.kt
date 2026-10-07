package com.fotopotok.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fotopotok.app.R
import com.fotopotok.app.api.Photo
import com.fotopotok.app.databinding.ItemGroupBinding
import com.fotopotok.app.databinding.ItemPhotoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoAdapter(
    private val imageUrl: (Photo) -> String?,
    private val onLike: (Photo) -> Unit,
    private val onSend: (Photo) -> Unit,
    private val onDelete: (Photo) -> Unit,
    private val onGroupDelete: (List<Photo>) -> Unit,
    private val onSelect: (Photo) -> Unit,
    private val onGroupLike: (String) -> Unit
) : ListAdapter<FeedItem, RecyclerView.ViewHolder>(DIFF) {

    var meId: String? = null
    var selectMode: Boolean = false
    var selected: Set<String> = emptySet()

    companion object {
        private const val TYPE_SINGLE = 1
        private const val TYPE_GROUP = 2

        private val DIFF = object : DiffUtil.ItemCallback<FeedItem>() {
            override fun areItemsTheSame(a: FeedItem, b: FeedItem): Boolean = when {
                a is FeedItem.Single && b is FeedItem.Single -> a.photo.id == b.photo.id
                a is FeedItem.Group && b is FeedItem.Group ->
                    a.photos.firstOrNull()?.group == b.photos.firstOrNull()?.group
                else -> false
            }
            override fun areContentsTheSame(a: FeedItem, b: FeedItem): Boolean = a == b
        }
    }

    class SingleVH(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)
    class GroupVH(val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is FeedItem.Single) TYPE_SINGLE else TYPE_GROUP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_SINGLE) {
            SingleVH(ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            GroupVH(ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is FeedItem.Single -> bindSingle(holder as SingleVH, item.photo)
            is FeedItem.Group -> bindGroup(holder as GroupVH, item.photos)
        }
    }

    private fun bindSingle(holder: SingleVH, photo: Photo) {
        val b = holder.binding
        val ctx = holder.itemView.context
        imageUrl(photo)?.let { b.imgPhoto.load(it) { crossfade(true) } }
        b.authorName.text = photo.authorName
        b.time.text = fmtTime(photo.createdAt)

        if (photo.caption.isNullOrBlank()) b.caption.visibility = View.GONE
        else { b.caption.visibility = View.VISIBLE; b.caption.text = photo.caption }

        b.processedBadge.visibility = if (photo.likeCount > 0) View.VISIBLE else View.GONE
        val isOwn = meId != null && meId == photo.authorId
        b.deleteBtn.visibility = if (isOwn && !selectMode) View.VISIBLE else View.GONE
        b.selectOverlay.visibility = if (selectMode) View.VISIBLE else View.GONE
        b.selTick.visibility = if (selected.contains(photo.id)) View.VISIBLE else View.GONE

        b.chatStatusDot.background = dotFor(photo.chatStatus, ctx)
        b.chatStatus.text = statusText(photo.chatStatus, photo.chatError, ctx)
        b.chatSendBtn.visibility =
            if (selectMode || photo.chatStatus in setOf("failed", "no-chat", "none"))
                View.VISIBLE else View.GONE

        val liked = photo.likedByMe || photo.likeCount > 0
        b.likeBtn.text = if (liked) "♥ Обработано (${photo.likeCount})" else "♡ Отметить обработанным"
        b.likeBtn.setTextColor(if (liked) ctx.getColor(R.color.good) else Color.parseColor("#A99F8D"))

        b.mediaFrame.setOnClickListener {
            if (selectMode) onSelect(photo) else onLike(photo)
        }
        b.likeBtn.setOnClickListener { onLike(photo) }
        b.chatSendBtn.setOnClickListener { onSend(photo) }
        b.deleteBtn.setOnClickListener { onDelete(photo) }
    }

    private fun bindGroup(holder: GroupVH, photos: List<Photo>) {
        val b = holder.binding
        val ctx = holder.itemView.context
        val first = photos.first()
        val total = photos.size
        val isOwn = meId != null && first.authorId == meId
        b.btnGroupDelete.visibility = if (isOwn && !selectMode) View.VISIBLE else View.GONE
        b.btnGroupDelete.setOnClickListener { onGroupDelete(photos) }

        val thumbs = listOf(b.groupThumb1, b.groupThumb2, b.groupThumb3)
        for (i in thumbs.indices) {
            if (i < photos.size) {
                thumbs[i].visibility = View.VISIBLE
                imageUrl(photos[i])?.let { thumbs[i].load(it) { crossfade(true) } }
            } else {
                thumbs[i].visibility = View.INVISIBLE
            }
        }
        b.moreBadge.visibility = if (total > 3) View.VISIBLE else View.GONE
        if (total > 3) b.moreBadge.text = "+" + (total - 3)
        b.processedGroupBadge.visibility =
            if (photos.all { it.likeCount > 0 }) View.VISIBLE else View.GONE

        b.groupAuthor.text = first.authorName
        b.groupTime.text = fmtTime(photos.minOf { it.createdAt })
        b.groupTitle.text = first.groupTitle?.takeIf { it.isNotBlank() } ?: "Пачка из $total"
        b.groupCount.text = "$total фото в пачке"

        val allDone = photos.all { it.likeCount > 0 }
        b.btnGroupLike.text = if (allDone) "✓ Пачка обработана" else "Отметить пачку"
        b.btnGroupLike.setTextColor(if (allDone) ctx.getColor(R.color.good) else Color.parseColor("#A99F8D"))
        b.btnGroupLike.setOnClickListener {
            first.group?.let { onGroupLike(it) }
        }
    }

    private fun dotFor(status: String?, ctx: android.content.Context): android.graphics.drawable.Drawable {
        val color = when (status) {
            "sent" -> ctx.getColor(R.color.good)
            "pending" -> ctx.getColor(R.color.amber)
            else -> Color.parseColor("#3A332A")
        }
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun statusText(status: String?, err: String?, ctx: android.content.Context): String = when (status) {
        "pending" -> ctx.getString(R.string.chat_send_status_pending)
        "sent" -> ctx.getString(R.string.chat_send_status_sent)
        "failed" -> if (err.isNullOrBlank()) ctx.getString(R.string.chat_send_status_failed) else "не ушло: $err"
        "no-chat" -> ctx.getString(R.string.chat_send_status_nochat)
        else -> ctx.getString(R.string.chat_send_status_none)
    }

    private fun fmtTime(ts: Long): String {
        val d = Date(ts)
        val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(d)
        return if (day == today) hm
        else SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(d)
    }
}
