
package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.core.OlcrtcManager
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.databinding.ItemRecyclerMainNewBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.performLightHapticFeedback
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.viewmodel.MainViewModel
import java.util.Collections

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
        private const val VIEW_TYPE_ITEM_NEW = 3
    }

    private val doubleColumnDisplay = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    private val serverCardStyle = SettingsManager.getServerCardStyle()
    private var data: MutableList<ServersCache> = mutableListOf()

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        val updated = newData?.toMutableList() ?: mutableListOf()

        if (position >= 0 && position in updated.indices) {
            data = updated
            notifyItemChanged(position)
            return
        }

        val diffCallback = ServerDiffCallback(data, updated)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        data = updated
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = data.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val context = holder.itemMainBinding.root.context
            val guid = data[position].guid
            val profile = data[position].profile

            //Name address
            holder.itemMainBinding.tvName.text = profile.remarks
            holder.itemMainBinding.tvStatistics.text = getAddress(profile)
            holder.itemMainBinding.tvType.text = getProtocolDescription(profile)

            //TestResult
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            holder.itemMainBinding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
            if ((aff?.testDelayMillis ?: 0L) < 0L) {
                holder.itemMainBinding.tvTestResult.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                        holder.itemMainBinding.tvTestResult,
                        androidx.appcompat.R.attr.colorError
                    )
                )
            } else {
                holder.itemMainBinding.tvTestResult.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        holder.itemMainBinding.tvTestResult,
                        com.google.android.material.R.attr.colorTertiary
                    )
                )
            }

            //selected state & segmented corners
            val isSelected = guid == MmkvManager.getSelectServer()
            applyCardShape(holder.itemMainBinding.root, holder.shapeState, position, isSelected)

            //subscription remarks
            val subRemarks = getSubscriptionRemarks(profile)
            holder.itemMainBinding.tvSubscription.text = subRemarks
            holder.itemMainBinding.layoutSubscription.visibility = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE

            //layout
            if (doubleColumnDisplay) {
                holder.itemMainBinding.layoutShare.visibility = View.GONE
                holder.itemMainBinding.layoutEdit.visibility = View.GONE
                holder.itemMainBinding.layoutRemove.visibility = View.GONE
                holder.itemMainBinding.layoutMore.visibility = View.VISIBLE

                holder.itemMainBinding.layoutMore.setOnClickListener {
                    adapterListener?.onShare(guid, profile, position, true)
                }
            } else {
                holder.itemMainBinding.layoutShare.visibility = View.VISIBLE
                holder.itemMainBinding.layoutEdit.visibility = View.VISIBLE
                holder.itemMainBinding.layoutRemove.visibility = View.VISIBLE
                holder.itemMainBinding.layoutMore.visibility = View.GONE

                holder.itemMainBinding.layoutShare.setOnClickListener {
                    adapterListener?.onShare(guid, profile, position, false)
                }

                holder.itemMainBinding.layoutEdit.setOnClickListener {
                    adapterListener?.onEdit(guid, position, profile)
                }
                holder.itemMainBinding.layoutRemove.setOnClickListener {
                    adapterListener?.onRemove(guid, position)
                }
            }

            holder.itemMainBinding.infoContainer.setOnClickListener {
                it.performLightHapticFeedback()
                adapterListener?.onSelectServer(guid)
            }

            applyPressFeedback(holder.itemMainBinding.root)
        }

        if (holder is NewMainViewHolder) {
            val context = holder.itemMainNewBinding.root.context
            val guid = data[position].guid
            val profile = data[position].profile

            holder.itemMainNewBinding.tvName.text = profile.remarks
            holder.itemMainNewBinding.tvType.text = getProtocolDescription(profile)

            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            holder.itemMainNewBinding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
            if ((aff?.testDelayMillis ?: 0L) < 0L) {
                holder.itemMainNewBinding.tvTestResult.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        holder.itemMainNewBinding.tvTestResult,
                        androidx.appcompat.R.attr.colorError
                    )
                )
            } else {
                holder.itemMainNewBinding.tvTestResult.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        holder.itemMainNewBinding.tvTestResult,
                        com.google.android.material.R.attr.colorTertiary
                    )
                )
            }

            val isSelected = guid == MmkvManager.getSelectServer()
            applyCardShape(holder.itemMainNewBinding.root, holder.shapeState, position, isSelected)

            val subRemarks = getSubscriptionRemarks(profile)
            holder.itemMainNewBinding.tvSubscription.text = subRemarks
            holder.itemMainNewBinding.layoutSubscription.visibility =
                if (subRemarks.isEmpty()) View.GONE else View.VISIBLE

            holder.itemMainNewBinding.btnMore.setOnClickListener {
                val popup = PopupMenu(context, it)
                popup.menuInflater.inflate(R.menu.menu_server_overflow, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_share -> {
                            adapterListener?.onShare(guid, profile, position, false)
                            true
                        }
                        R.id.action_edit -> {
                            adapterListener?.onEdit(guid, position, profile)
                            true
                        }
                        R.id.action_delete -> {
                            adapterListener?.onRemove(guid, position)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }

            holder.itemMainNewBinding.infoContainer.setOnClickListener {
                it.performLightHapticFeedback()
                adapterListener?.onSelectServer(guid)
            }

            applyPressFeedback(holder.itemMainNewBinding.root)
        }

    }

    class CardShapeState {
        var appliedTop = -1f
        var appliedBottom = -1f
        var appliedColor = 0
        var appliedElevation = -1f
        var shapeAnimator: android.animation.ValueAnimator? = null
    }

    private fun applyCardShape(
        card: com.google.android.material.card.MaterialCardView,
        state: CardShapeState,
        position: Int,
        isSelected: Boolean
    ) {
        val density = card.resources.displayMetrics.density
        val large = 24f * density
        val small = 5f * density
        val lastIndex = data.size - 1
        val (targetTop, targetBottom) = when {
            isSelected -> large to large
            data.size <= 1 -> large to large
            position == 0 -> large to small
            position == lastIndex -> small to large
            else -> small to small
        }
        val targetColor = com.google.android.material.color.MaterialColors.getColor(
            card,
            if (isSelected) com.google.android.material.R.attr.colorSurfaceBright
            else com.google.android.material.R.attr.colorSurfaceContainerHigh
        )
        val targetElevation = if (isSelected) 4f * density else 0f

        state.shapeAnimator?.cancel()
        state.shapeAnimator = null

        val canAnimate = state.appliedTop >= 0f &&
            (state.appliedTop != targetTop || state.appliedBottom != targetBottom ||
             state.appliedColor != targetColor || state.appliedElevation != targetElevation)

        if (!canAnimate) {
            setCardCorners(card, targetTop, targetBottom)
            card.setCardBackgroundColor(targetColor)
            card.cardElevation = targetElevation
            state.appliedTop = targetTop
            state.appliedBottom = targetBottom
            state.appliedColor = targetColor
            state.appliedElevation = targetElevation
            return
        }

        val startTop = state.appliedTop
        val startBottom = state.appliedBottom
        val startColor = state.appliedColor
        val startElevation = state.appliedElevation
        val argb = android.animation.ArgbEvaluator()
        state.shapeAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedValue as Float
                setCardCorners(card, startTop + (targetTop - startTop) * f, startBottom + (targetBottom - startBottom) * f)
                card.setCardBackgroundColor(argb.evaluate(f, startColor, targetColor) as Int)
                card.cardElevation = startElevation + (targetElevation - startElevation) * f
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    state.appliedTop = targetTop
                    state.appliedBottom = targetBottom
                    state.appliedColor = targetColor
                    state.appliedElevation = targetElevation
                }
            })
            start()
        }
    }

    /**
     * Adds a subtle scale feedback when the user presses or releases a card.
     * This matches the Material 3 Expressive emphasis on tactile, responsive motion.
     */
    private fun applyPressFeedback(card: com.google.android.material.card.MaterialCardView) {
        card.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .setDuration(100L)
                        .start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150L)
                        .start()
                }
            }
            false
        }
    }

    private fun setCardCorners(card: com.google.android.material.card.MaterialCardView, top: Float, bottom: Float) {
        card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(top)
            .setTopRightCornerSize(top)
            .setBottomLeftCornerSize(bottom)
            .setBottomRightCornerSize(bottom)
            .build()
    }

    override fun onViewRecycled(holder: BaseViewHolder) {
        super.onViewRecycled(holder)
        if (holder is MainViewHolder) {
            holder.shapeState.shapeAnimator?.cancel()
            holder.shapeState.shapeAnimator = null
            holder.shapeState.appliedTop = -1f
            holder.shapeState.appliedBottom = -1f
            holder.shapeState.appliedColor = 0
            holder.shapeState.appliedElevation = -1f
            holder.itemMainBinding.root.setOnTouchListener(null)
            holder.itemMainBinding.root.scaleX = 1f
            holder.itemMainBinding.root.scaleY = 1f
        }
        if (holder is NewMainViewHolder) {
            holder.shapeState.shapeAnimator?.cancel()
            holder.shapeState.shapeAnimator = null
            holder.shapeState.appliedTop = -1f
            holder.shapeState.appliedBottom = -1f
            holder.shapeState.appliedColor = 0
            holder.shapeState.appliedElevation = -1f
            holder.itemMainNewBinding.root.setOnTouchListener(null)
            holder.itemMainNewBinding.root.scaleX = 1f
            holder.itemMainNewBinding.root.scaleY = 1f
        }
    }

    /**
     * Gets the server address information
     * Hides part of IP or domain information for privacy protection
     * @param profile The server configuration
     * @return Formatted address string
     */
    private fun getAddress(profile: ProfileItem): String {
        if (profile.configType == EConfigType.OLCRTC) {
            return OlcrtcManager.providerUrl(
                profile.olcrtcCarrier.orEmpty(),
                profile.olcrtcRoomId,
                profile.olcrtcServerUrl
            )
        }
        return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
    }

    /**
     * Gets the subscription remarks information
     * @param profile The server configuration
     * @return Subscription remarks string, or empty string if none
     */
    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        val subRemarks =
            if (mainViewModel.subscriptionId.isEmpty())
                MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
            else
                null
        return subRemarks?.toString() ?: ""
    }

    private fun getProtocolDescription(profile: ProfileItem): String {
        if (profile.configType.isComplexType()) {
            return profile.configType.name
        }

        if (profile.configType == EConfigType.OLCRTC) {
            val parts = mutableListOf<String>()
            parts.add("olcRTC")
            profile.olcrtcCarrier?.let { parts.add(it) }
            profile.olcrtcTransport?.let { parts.add(it) }
            return parts.joinToString(" / ")
        }

        val parts = mutableListOf<String>()
        parts.add(profile.configType.name)

        // Transport: hide tcp or blank
        profile.network?.let { net ->
            if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) {
                parts.add(net)
            }
        }

        // Security: hide blank or tls
        profile.security?.let { sec ->
            if (sec.isNotBlank()) {
                if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                    parts.add("$sec insecure") // TODO
                } else {
                    parts.add(sec)
                }
            }
        }

        return parts.joinToString(" / ")
    }

    fun removeServerSub(guid: String, position: Int) {
        val idx = data.indexOfFirst { it.guid == guid }
        if (idx >= 0) {
            data.removeAt(idx)
            notifyItemRemoved(idx)
            notifyItemRangeChanged(idx, data.size - idx)
        }
    }

    fun setSelectServer(fromPosition: Int, toPosition: Int) {
        notifyItemChanged(fromPosition)
        notifyItemChanged(toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_ITEM_NEW ->
                NewMainViewHolder(ItemRecyclerMainNewBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else ->
                FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == data.size) {
            VIEW_TYPE_FOOTER
        } else if (serverCardStyle == "new") {
            VIEW_TYPE_ITEM_NEW
        } else {
            VIEW_TYPE_ITEM
        }
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

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root), ItemTouchHelperViewHolder {
        val shapeState = CardShapeState()
    }

    class NewMainViewHolder(val itemMainNewBinding: ItemRecyclerMainNewBinding) :
        BaseViewHolder(itemMainNewBinding.root), ItemTouchHelperViewHolder {
        val shapeState = CardShapeState()
    }

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mainViewModel.swapServer(fromPosition, toPosition)
        if (fromPosition < data.size && toPosition < data.size) {
            Collections.swap(data, fromPosition, toPosition)
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        // do nothing
    }

    override fun onItemDismiss(position: Int) {
    }


    /**
     * DiffUtil callback that animates list changes in the main server list.
     * Two items are the same when their GUID matches; contents differ when the
     * displayed data (name, address, type, test result, selection) changes.
     */
    private class ServerDiffCallback(
        private val oldList: List<ServersCache>,
        private val newList: List<ServersCache>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].guid == newList[newItemPosition].guid
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.profile.remarks == newItem.profile.remarks &&
                    oldItem.profile.subscriptionId == newItem.profile.subscriptionId &&
                    oldItem.profile.configType == newItem.profile.configType &&
                    oldItem.profile.network == newItem.profile.network &&
                    oldItem.profile.security == newItem.profile.security
        }
    }
}
