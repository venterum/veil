package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerSubSettingBinding
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.SubscriptionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubSettingRecyclerAdapter(
    private val viewModel: SubscriptionsViewModel,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<SubSettingRecyclerAdapter.MainViewHolder>(), ItemTouchHelperAdapter {

    override fun getItemCount() = viewModel.getAll().size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val subscriptions = viewModel.getAll()
        val subId = subscriptions[position].guid
        val subItem = subscriptions[position].subscription
        val binding = holder.itemSubSettingBinding
        val context = binding.root.context

        val displayName = if (subItem.profileTitle.isNotEmpty()) subItem.profileTitle else subItem.remarks
        binding.tvName.text = displayName

        binding.tvUrl.apply {
            text = subItem.url
            visibility = if (TextUtils.isEmpty(subItem.url)) View.GONE else View.VISIBLE
        }

        binding.chkEnable.isChecked = subItem.enabled

        val userinfo = Utils.parseSubscriptionUserinfo(subItem.subscriptionUserinfo)
        if (userinfo != null && (userinfo.upload > 0 || userinfo.download > 0 || userinfo.total > 0 || userinfo.expire > 0)) {
            binding.layoutTraffic.visibility = View.VISIBLE
            val used = userinfo.upload + userinfo.download
            val usedStr = Utils.formatBytes(used)
            val totalStr = if (userinfo.total > 0) " / ${Utils.formatBytes(userinfo.total)}" else ""
            binding.tvTrafficValue.text = "$usedStr$totalStr"

            val progress = if (userinfo.total > 0) {
                ((used.toDouble() / userinfo.total) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            binding.progressTraffic.progress = progress

            if (userinfo.expire > 0) {
                binding.tvExpire.visibility = View.VISIBLE
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(userinfo.expire * 1000))
                binding.tvExpire.text = context.getString(R.string.title_expire, dateStr)
                val nowSec = System.currentTimeMillis() / 1000
                if (userinfo.expire < nowSec) {
                    binding.tvExpire.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                        binding.root, androidx.appcompat.R.attr.colorError
                    ))
                } else {
                    binding.tvExpire.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                        binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant
                    ))
                }
            } else {
                binding.tvExpire.visibility = View.GONE
            }
        } else {
            binding.layoutTraffic.visibility = View.GONE
        }

        binding.tvAnnounce.apply {
            visibility = if (subItem.announce.isNotEmpty()) View.VISIBLE else View.GONE
            text = subItem.announce
        }

        binding.btnSupport.apply {
            visibility = if (subItem.supportUrl.isNotEmpty()) View.VISIBLE else View.GONE
            setOnClickListener {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(subItem.supportUrl)))
                } catch (_: Exception) {}
            }
        }

        binding.btnWeb.apply {
            visibility = if (subItem.profileWebPageUrl.isNotEmpty()) View.VISIBLE else View.GONE
            setOnClickListener {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(subItem.profileWebPageUrl)))
                } catch (_: Exception) {}
            }
        }

        binding.tvLastUpdated.apply {
            val ts = Utils.formatTimestamp(subItem.lastUpdated)
            if (ts.isNotEmpty()) {
                visibility = View.VISIBLE
                text = ts
            } else {
                visibility = View.GONE
            }
        }

        binding.layoutEdit.setOnClickListener {
            adapterListener?.onEdit(subId, position)
        }

        binding.layoutRemove.setOnClickListener {
            adapterListener?.onRemove(subId, position)
        }

        binding.layoutShare.apply {
            if (subItem.url.isEmpty()) {
                visibility = View.INVISIBLE
            } else {
                visibility = View.VISIBLE
                setOnClickListener {
                    adapterListener?.onShare(subItem.url)
                }
            }
        }

        binding.chkEnable.setOnCheckedChangeListener { it, isChecked ->
            if (!it.isPressed) return@setOnCheckedChangeListener
            subItem.enabled = isChecked
            viewModel.update(subId, subItem)
        }

        applyCardShape(binding.root, holder.shapeState, position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerSubSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class CardShapeState {
        var appliedTop = -1f
        var appliedBottom = -1f
    }

    private fun applyCardShape(
        card: com.google.android.material.card.MaterialCardView,
        state: CardShapeState,
        position: Int
    ) {
        val density = card.resources.displayMetrics.density
        val large = 24f * density
        val small = 5f * density
        val lastIndex = viewModel.getAll().size - 1

        val (targetTop, targetBottom) = when {
            viewModel.getAll().size <= 1 -> large to large
            position == 0 -> large to small
            position == lastIndex -> small to large
            else -> small to small
        }

        if (state.appliedTop >= 0f && state.appliedTop == targetTop && state.appliedBottom == targetBottom) return

        setCardCorners(card, targetTop, targetBottom)
        state.appliedTop = targetTop
        state.appliedBottom = targetBottom
    }

    private fun setCardCorners(card: com.google.android.material.card.MaterialCardView, top: Float, bottom: Float) {
        card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(top)
            .setTopRightCornerSize(top)
            .setBottomLeftCornerSize(bottom)
            .setBottomRightCornerSize(bottom)
            .build()
    }

    override fun onViewRecycled(holder: MainViewHolder) {
        super.onViewRecycled(holder)
        holder.shapeState.appliedTop = -1f
        holder.shapeState.appliedBottom = -1f
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerSubSettingBinding) :
        BaseViewHolder(itemSubSettingBinding.root), ItemTouchHelperViewHolder {
        val shapeState = CardShapeState()
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            (itemView as? com.google.android.material.card.MaterialCardView)?.cardElevation =
                6f * itemView.resources.displayMetrics.density
        }

        fun onItemClear() {
            (itemView as? com.google.android.material.card.MaterialCardView)?.cardElevation = 0f
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        viewModel.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        adapterListener?.onRefreshData()
    }

    override fun onItemDismiss(position: Int) {
    }
}
