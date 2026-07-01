package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemRecyclerBypassListBinding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.viewmodel.PerAppProxyViewModel

class PerAppProxyAdapter(
    val apps: List<AppInfo>,
    val viewModel: PerAppProxyViewModel
) : RecyclerView.Adapter<PerAppProxyAdapter.BaseViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is AppViewHolder) {
            val index = position - 1
            val appInfo = apps[index]
            holder.bind(appInfo)
            holder.applyCardShape(index, apps.size)
        }
    }

    override fun getItemCount() = apps.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val ctx = parent.context

        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = View(ctx)
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                )
                BaseViewHolder(view)
            }

            else -> AppViewHolder(ItemRecyclerBypassListBinding.inflate(LayoutInflater.from(ctx), parent, false))
        }
    }

    override fun getItemViewType(position: Int) = if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class AppViewHolder(private val itemBypassBinding: ItemRecyclerBypassListBinding) : BaseViewHolder(itemBypassBinding.root),
        View.OnClickListener {
        private lateinit var appInfo: AppInfo

        fun bind(appInfo: AppInfo) {
            this.appInfo = appInfo

            itemBypassBinding.icon.setImageDrawable(appInfo.appIcon)
            itemBypassBinding.name.text = if (appInfo.isSystemApp) {
                String.format("** %s", appInfo.appName)
            } else {
                appInfo.appName
            }

            itemBypassBinding.packageName.text = appInfo.packageName
            itemBypassBinding.checkBox.isChecked = viewModel.contains(appInfo.packageName)

            itemView.setOnClickListener(this)
        }

        fun applyCardShape(index: Int, count: Int) {
            val card = itemBypassBinding.root
            val density = card.resources.displayMetrics.density
            val large = 24f * density
            val small = 4f * density
            val (topCorner, bottomCorner) = when {
                count <= 1 -> large to large
                index == 0 -> large to small
                index == count - 1 -> small to large
                else -> small to small
            }
            card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(topCorner)
                .setTopRightCornerSize(topCorner)
                .setBottomLeftCornerSize(bottomCorner)
                .setBottomRightCornerSize(bottomCorner)
                .build()
        }

        override fun onClick(v: View?) {
            val packageName = appInfo.packageName
            viewModel.toggle(packageName)
            itemBypassBinding.checkBox.isChecked = viewModel.contains(packageName)
        }
    }
}
