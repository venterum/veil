package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.databinding.ItemDrawerBinding
import com.v2ray.ang.databinding.ItemDrawerHeaderBinding

sealed class DrawerEntry {
    data class Header(val titleRes: Int) : DrawerEntry()
    data class Item(val id: Int, val iconRes: Int, val titleRes: Int) : DrawerEntry()
}

class DrawerAdapter(
    private val entries: List<DrawerEntry>,
    private val onItemClick: (Int, android.view.View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemCount() = entries.size

    override fun getItemViewType(position: Int) =
        if (entries[position] is DrawerEntry.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemDrawerHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemDrawerBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = entries[position]) {
            is DrawerEntry.Header -> {
                (holder as HeaderViewHolder).binding.drawerHeader.setText(entry.titleRes)
            }

            is DrawerEntry.Item -> {
                val binding = (holder as ItemViewHolder).binding
                binding.drawerIcon.setImageResource(entry.iconRes)
                binding.drawerTitle.setText(entry.titleRes)
                binding.root.setOnClickListener { onItemClick(entry.id, it) }
                applyCardShape(binding.root, position)
            }
        }
    }

    private fun applyCardShape(card: MaterialCardView, position: Int) {
        val density = card.resources.displayMetrics.density
        val large = 24f * density
        val small = 4f * density
        val isFirst = position == 0 || entries[position - 1] is DrawerEntry.Header
        val isLast = position == entries.size - 1 || entries[position + 1] is DrawerEntry.Header
        val topCorner = if (isFirst) large else small
        val bottomCorner = if (isLast) large else small
        card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(topCorner)
            .setTopRightCornerSize(topCorner)
            .setBottomLeftCornerSize(bottomCorner)
            .setBottomRightCornerSize(bottomCorner)
            .build()
    }

    class HeaderViewHolder(val binding: ItemDrawerHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class ItemViewHolder(val binding: ItemDrawerBinding) : RecyclerView.ViewHolder(binding.root)
}
