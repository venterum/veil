package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemRecyclerTaskerBinding

class TaskerAdapter : RecyclerView.Adapter<TaskerAdapter.ViewHolder>() {
    private val items = mutableListOf<String>()
    private var selectedPosition = RecyclerView.NO_POSITION

    fun submitList(newItems: List<String>, selectedPosition: Int = RecyclerView.NO_POSITION) {
        items.clear()
        items.addAll(newItems)
        this.selectedPosition = selectedPosition
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        if (position < 0 || position >= items.size) return
        val previous = selectedPosition
        selectedPosition = position
        notifyItemChanged(previous)
        notifyItemChanged(selectedPosition)
    }

    fun getSelectedPosition(): Int = selectedPosition

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecyclerTaskerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position == selectedPosition)
    }

    inner class ViewHolder(private val binding: ItemRecyclerTaskerBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(name: String, isSelected: Boolean) {
            binding.tvName.text = name
            binding.radioButton.isChecked = isSelected
        }

        override fun onClick(v: View?) {
            val previous = selectedPosition
            selectedPosition = bindingAdapterPosition
            notifyItemChanged(previous)
            notifyItemChanged(selectedPosition)
        }
    }
}
