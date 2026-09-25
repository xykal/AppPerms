package app.appsperms.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.appsperms.R
import app.appsperms.databinding.ItemAppBinding
import app.appsperms.databinding.ItemHeaderBinding
import app.appsperms.model.AppEntry

class AppListAdapter(
    private val onOpen: (AppEntry) -> Unit,
    private val onChangeOverlay: (AppEntry) -> Unit,
    private val onLoadIcon: ((String) -> Drawable?)? = null,
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.App -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemHeaderBinding.inflate(inflater, parent, false))
        } else {
            AppVH(ItemAppBinding.inflate(inflater, parent, false), onOpen, onChangeOverlay, onLoadIcon)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> (holder as HeaderVH).bind(item)
            is ListItem.App -> (holder as AppVH).bind(item.entry)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_STATUS) && holder is AppVH) {
            val item = getItem(position)
            if (item is ListItem.App) {
                holder.updateStatusOnly(item.entry)
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    class HeaderVH(private val b: ItemHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ListItem.Header) = with(b) {
            headerTitle.text = item.title
            headerCount.text = item.count.toString()
        }
    }

    class AppVH(
        private val b: ItemAppBinding,
        private val onOpen: (AppEntry) -> Unit,
        private val onChangeOverlay: (AppEntry) -> Unit,
        private val onLoadIcon: ((String) -> Drawable?)?,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: AppEntry) = with(b) {
            label.text = item.label
            pkg.text = item.packageName
            val currentIcon = item.icon ?: onLoadIcon?.invoke(item.packageName)
            if (currentIcon != null) {
                icon.setImageDrawable(currentIcon)
            } else {
                icon.setImageResource(R.drawable.bg_avatar)
            }
            badgeUid.text = "uid ${item.uid}"
            badgeSystem.isVisible = item.isSystem && false
            badgeDeclares.isVisible = item.declaresOverlay

            // Clone / Work profile / Multi-user badges
            val cloneBadge = item.cloneBadge
            badgeClone.isVisible = cloneBadge != null
            if (cloneBadge != null) {
                badgeClone.text = cloneBadge
                // Color by type
                when {
                    item.isClone -> {
                        badgeClone.setBackgroundResource(R.drawable.bg_chip_small)
                        badgeClone.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF7C3AED.toInt())
                    }
                    item.isWorkProfile -> {
                        badgeClone.setBackgroundResource(R.drawable.bg_chip_small)
                        badgeClone.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF0EA5E9.toInt())
                    }
                    else -> {
                        badgeClone.setBackgroundResource(R.drawable.bg_chip_small)
                        badgeClone.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF6B7280.toInt())
                    }
                }
            }
            badgeUserId.isVisible = item.userId != 0
            if (item.userId != 0) {
                badgeUserId.text = "User ${item.userId}"
            }

            statusChip.bindStatusChip(item.overlayStatus)

            statusChip.setOnClickListener { onChangeOverlay(item) }
            rowRoot.setOnClickListener { onOpen(item) }
            rowRoot.setOnLongClickListener {
                onChangeOverlay(item)
                true
            }
        }

        fun updateStatusOnly(item: AppEntry) = with(b) {
            statusChip.bindStatusChip(item.overlayStatus)
            statusChip.setOnClickListener { onChangeOverlay(item) }
            rowRoot.setOnLongClickListener {
                onChangeOverlay(item)
                true
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
        const val PAYLOAD_STATUS = "payload_status"

        private val DIFF = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(old: ListItem, new: ListItem) = old.key == new.key
            override fun areContentsTheSame(old: ListItem, new: ListItem): Boolean {
                if (old is ListItem.App && new is ListItem.App) {
                    return old.entry.overlayStatus == new.entry.overlayStatus &&
                        old.entry.label == new.entry.label &&
                        old.entry.declaresOverlay == new.entry.declaresOverlay &&
                        old.entry.isSystem == new.entry.isSystem &&
                        old.entry.userId == new.entry.userId &&
                        old.entry.isClone == new.entry.isClone &&
                        old.entry.cloneBadge == new.entry.cloneBadge
                }
                return old == new
            }

            override fun getChangePayload(old: ListItem, new: ListItem): Any? {
                if (old is ListItem.App && new is ListItem.App) {
                    if (old.entry.packageName == new.entry.packageName &&
                        old.entry.userId == new.entry.userId &&
                        old.entry.overlayStatus != new.entry.overlayStatus
                    ) {
                        return PAYLOAD_STATUS
                    }
                }
                return null
            }
        }
    }
}
