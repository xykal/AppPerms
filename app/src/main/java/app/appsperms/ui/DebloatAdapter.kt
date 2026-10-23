package app.appsperms.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.appsperms.R
import app.appsperms.core.DebloatPackage
import app.appsperms.core.DebloatState
import app.appsperms.core.DebloatTier
import app.appsperms.databinding.ItemDebloatAppBinding

data class DebloatUiRow(
    val item: DebloatPackage,
    val label: String,
)

class DebloatAdapter(
    private val onAction: (DebloatUiRow) -> Unit,
) : ListAdapter<DebloatUiRow, DebloatAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemDebloatAppBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemDebloatAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: DebloatUiRow) = with(binding) {
            val context = root.context
            debloatLabel.text = row.label
            debloatPackage.text = row.item.packageName
            val tier = when (row.item.classification.tier) {
                DebloatTier.SAFE -> context.getString(R.string.device_care_tier_safe)
                DebloatTier.ADVANCED -> context.getString(R.string.device_care_tier_advanced)
                DebloatTier.EXPERT -> context.getString(R.string.device_care_tier_expert)
                DebloatTier.PROTECTED -> context.getString(R.string.device_care_tier_protected)
            }
            val state = when (row.item.state) {
                DebloatState.ENABLED -> context.getString(R.string.device_care_state_enabled)
                DebloatState.DISABLED -> context.getString(R.string.device_care_state_disabled)
                DebloatState.UNINSTALLED_USER -> context.getString(R.string.device_care_state_uninstalled)
                DebloatState.UNKNOWN -> context.getString(R.string.status_unknown)
            }
            debloatMeta.text = "$tier · $state · user ${row.item.userId}"

            val protected = row.item.classification.tier == DebloatTier.PROTECTED
            debloatAction.isEnabled = !protected && row.item.state != DebloatState.UNKNOWN
            debloatAction.text = context.getString(
                when {
                    protected -> R.string.device_care_protected
                    row.item.state == DebloatState.ENABLED -> R.string.device_care_manage
                    else -> R.string.device_care_restore
                },
            )
            debloatAction.setOnClickListener { onAction(row) }
            root.setOnClickListener { if (debloatAction.isEnabled) onAction(row) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DebloatUiRow>() {
        override fun areItemsTheSame(oldItem: DebloatUiRow, newItem: DebloatUiRow): Boolean =
            oldItem.item.packageName == newItem.item.packageName && oldItem.item.userId == newItem.item.userId

        override fun areContentsTheSame(oldItem: DebloatUiRow, newItem: DebloatUiRow): Boolean =
            oldItem == newItem
    }
}
