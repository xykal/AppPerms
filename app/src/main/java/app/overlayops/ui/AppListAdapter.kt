package app.overlayops.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.overlayops.databinding.ItemAppBinding
import app.overlayops.model.AppEntry

class AppListAdapter(
    private val onOpen: (AppEntry) -> Unit,
    private val onChangeOverlay: (AppEntry) -> Unit,
) : ListAdapter<AppEntry, AppListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onOpen,
        onChangeOverlay,
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val b: ItemAppBinding,
        private val onOpen: (AppEntry) -> Unit,
        private val onChangeOverlay: (AppEntry) -> Unit,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: AppEntry) = with(b) {
            label.text = item.label
            pkg.text = item.packageName
            icon.setImageDrawable(item.icon)
            badgeUid.text = "uid ${item.uid}"
            badgeSystem.isVisible = item.isSystem
            badgeDeclares.isVisible = item.declaresOverlay
            statusChip.bindStatusChip(item.overlayStatus)

            statusChip.setOnClickListener { onChangeOverlay(item) }
            rowRoot.setOnClickListener { onOpen(item) }
            rowRoot.setOnLongClickListener {
                onChangeOverlay(item)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppEntry>() {
            override fun areItemsTheSame(old: AppEntry, new: AppEntry) = old.packageName == new.packageName
            override fun areContentsTheSame(old: AppEntry, new: AppEntry) = old == new
        }
    }
}
