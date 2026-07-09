package com.v2ray.ang.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.children
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.databinding.ItemGroupContainerBinding
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
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

private data class Section(
    val subscriptionId: String,
    val name: String,
    val descText: String,
    val trafficUsed: String,
    val trafficTotal: String,
    val trafficProgress: Int,
    val expiryText: String,
    val isExpired: Boolean,
    val servers: List<ServersCache>,
)

class AllServerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?,
    private val showIcons: () -> Boolean,
    private val recyclerView: RecyclerView? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), ItemTouchHelperAdapter {

    companion object {
        private const val VIEW_TYPE_GROUP_CONTAINER = 0
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_ITEM_NEW = 2
        private const val VIEW_TYPE_FOOTER = 3
    }

    private val serverCardStyle = SettingsManager.getServerCardStyle()
    private var flatData: MutableList<ServersCache> = mutableListOf()
    private var sections: MutableList<Section> = mutableListOf()
    private val collapsedIds = mutableSetOf<String>()
    private var isGrouped = false

    init {
        loadCollapsedIds()
    }

    private fun loadCollapsedIds() {
        val saved = MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_COLLAPSED_IDS, "")
        collapsedIds.clear()
        saved?.split(",")?.filter { it.isNotEmpty() }?.let { collapsedIds.addAll(it) }
    }

    private fun saveCollapsedIds() {
        MmkvManager.encodeSettings(AppConfig.PREF_GROUP_COLLAPSED_IDS, collapsedIds.joinToString(","))
    }

    fun updateFlat(newData: MutableList<ServersCache>) {
        isGrouped = false
        flatData = newData.toMutableList()
        notifyDataSetChanged()
    }

    fun updateGrouped(servers: List<ServersCache>) {
        isGrouped = true
        sections.clear()

        val grouped = linkedMapOf<String, MutableList<ServersCache>>()
        for (sc in servers) {
            val subId = sc.profile.subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
            grouped.getOrPut(subId) { mutableListOf() }.add(sc)
        }

        val subsOrder = MmkvManager.decodeSubsList()
        val remaining = grouped.toMutableMap()

        for (subId in subsOrder) {
            val list = remaining.remove(subId) ?: continue
            buildSection(subId, list)?.let { sections.add(it) }
        }
        for ((subId, list) in remaining) {
            buildSection(subId, list)?.let { sections.add(it) }
        }

        notifyDataSetChanged()
    }

    private fun buildSection(subId: String, servers: List<ServersCache>): Section? {
        if (servers.isEmpty()) return null
        val subItem = MmkvManager.decodeSubscription(subId) ?: return null
        val name = if (subItem.profileTitle.isNotEmpty()) subItem.profileTitle else subItem.remarks.ifEmpty { subId }
        val descText = subItem.announce.ifEmpty { subItem.supportUrl }.ifEmpty { subItem.profileWebPageUrl }
        val userinfo = Utils.parseSubscriptionUserinfo(subItem.subscriptionUserinfo)
        val trafficUsed: String
        val trafficTotal: String
        val trafficProgress: Int
        val expiryText: String
        val isExpired: Boolean

        if (userinfo != null && (userinfo.upload > 0 || userinfo.download > 0 || userinfo.total > 0 || userinfo.expire > 0)) {
            val used = userinfo.upload + userinfo.download
            trafficUsed = Utils.formatBytes(used)
            trafficTotal = if (userinfo.total > 0) " / ${Utils.formatBytes(userinfo.total)}" else ""
            trafficProgress = if (userinfo.total > 0) ((used.toDouble() / userinfo.total) * 100).toInt().coerceIn(0, 100) else 0

            if (userinfo.expire > 0) {
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(userinfo.expire * 1000))
                expiryText = dateStr
                val nowSec = System.currentTimeMillis() / 1000
                isExpired = userinfo.expire < nowSec
            } else {
                expiryText = ""
                isExpired = false
            }
        } else {
            trafficUsed = ""
            trafficTotal = ""
            trafficProgress = 0
            expiryText = ""
            isExpired = false
        }

        return Section(
            subscriptionId = if (subId == AppConfig.DEFAULT_SUBSCRIPTION_ID) "" else subId,
            name = name,
            descText = descText,
            trafficUsed = trafficUsed,
            trafficTotal = trafficTotal,
            trafficProgress = trafficProgress,
            expiryText = expiryText,
            isExpired = isExpired,
            servers = servers,
        )
    }

    override fun getItemCount(): Int {
        return if (isGrouped) sections.size else flatData.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        if (isGrouped) return VIEW_TYPE_GROUP_CONTAINER
        return if (position == flatData.size) VIEW_TYPE_FOOTER
        else if (serverCardStyle == "new") VIEW_TYPE_ITEM_NEW else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM -> CardViewHolder(
                ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_ITEM_NEW -> NewCardViewHolder(
                ItemRecyclerMainNewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_GROUP_CONTAINER -> GroupContainerViewHolder(
                ItemGroupContainerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> footerView(parent)
        }
    }

    private fun footerView(parent: ViewGroup): RecyclerView.ViewHolder {
        val view = View(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (80 * parent.context.resources.displayMetrics.density).toInt()
            )
        }
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GroupContainerViewHolder -> bindGroupContainer(holder, position)
            is CardViewHolder -> bindClassicCard(holder, position)
            is NewCardViewHolder -> bindNewCard(holder, position)
        }
    }

    private fun getItemAt(position: Int): Pair<String, ProfileItem> {
        return flatData[position].guid to flatData[position].profile
    }

    private fun bindGroupContainer(holder: GroupContainerViewHolder, position: Int) {
        val section = sections[position]
        val binding = holder.binding
        val context = binding.root.context
        val isExpanded = section.subscriptionId !in collapsedIds

        binding.tvGroupName.text = section.name
        binding.ivExpandArrow.rotation = if (isExpanded) 0f else -90f
        binding.ivExpandArrow.contentDescription =
            if (isExpanded) context.getString(R.string.collapse_group)
            else context.getString(R.string.expand_group)

        if (section.descText.isNotEmpty()) {
            binding.tvGroupDesc.visibility = View.VISIBLE
            binding.tvGroupDesc.text = section.descText
        } else {
            binding.tvGroupDesc.visibility = View.GONE
        }

        if (section.trafficUsed.isNotEmpty()) {
            binding.layoutTraffic.visibility = View.VISIBLE
            binding.tvTrafficValue.text = "${section.trafficUsed}${section.trafficTotal}"
            binding.progressTraffic.progress = section.trafficProgress

            if (section.expiryText.isNotEmpty()) {
                binding.tvExpire.visibility = View.VISIBLE
                binding.tvExpire.text = context.getString(R.string.title_expire, section.expiryText)
                if (section.isExpired) {
                    binding.tvExpire.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            binding.root, androidx.appcompat.R.attr.colorError
                        )
                    )
                } else {
                    binding.tvExpire.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant
                        )
                    )
                }
            } else {
                binding.tvExpire.visibility = View.GONE
            }
        } else {
            binding.layoutTraffic.visibility = View.GONE
        }

        // Apply stacked-card corner radii based on group position
        val density = binding.root.context.resources.displayMetrics.density
        val largeRadius = 16f * density
        val smallRadius = 4f * density
        val isFirst = position == 0
        val isLast = position == sections.size - 1
        val topRadius = if (isFirst) largeRadius else smallRadius
        val bottomRadius = if (isLast) largeRadius else smallRadius
        binding.containerBg.shapeAppearanceModel = binding.containerBg.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCornerSize(topRadius)
            .setTopRightCornerSize(topRadius)
            .setBottomLeftCornerSize(bottomRadius)
            .setBottomRightCornerSize(bottomRadius)
            .build()

        binding.headerArea.setOnClickListener {
            it.performLightHapticFeedback()
            val wasExpanded = section.subscriptionId !in collapsedIds
            toggleGroup(section)

            val holder = recyclerView?.findViewHolderForAdapterPosition(position) as? GroupContainerViewHolder
            if (holder != null) {
                animateHolderExpand(holder, section, wasExpanded)
            } else {
                notifyItemChanged(position)
            }
        }

        if (isExpanded) {
            binding.serverList.visibility = View.VISIBLE
            binding.headerDivider.visibility = View.GONE
            inflateServerCards(binding.serverList, section)
            binding.ivExpandArrow.rotation = 0f
        } else {
            binding.serverList.visibility = View.GONE
            binding.headerDivider.visibility = View.GONE
            binding.serverList.removeAllViews()
            binding.ivExpandArrow.rotation = 180f
        }
    }

    private fun animateHolderExpand(holder: GroupContainerViewHolder, section: Section, wasExpanded: Boolean) {
        val binding = holder.binding
        val serverList = binding.serverList
        holder.currentAnimator?.cancel()

        if (wasExpanded) {
            // COLLAPSE
            val startHeight = serverList.height
            if (startHeight <= 0) {
                serverList.visibility = View.GONE
                serverList.removeAllViews()
                binding.ivExpandArrow.rotation = -90f
                return
            }

            binding.ivExpandArrow.animate()
                .rotation(180f)
                .setDuration(250)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()

            val animator = ValueAnimator.ofInt(startHeight, 0).apply {
                duration = 250
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { anim ->
                    val h = anim.animatedValue as Int
                    (serverList.layoutParams as LinearLayout.LayoutParams).height = h
                    serverList.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        serverList.visibility = View.GONE
                        serverList.removeAllViews()
                        (serverList.layoutParams as LinearLayout.LayoutParams).height = ViewGroup.LayoutParams.WRAP_CONTENT
                        holder.currentAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        serverList.visibility = View.GONE
                        serverList.removeAllViews()
                        (serverList.layoutParams as LinearLayout.LayoutParams).height = ViewGroup.LayoutParams.WRAP_CONTENT
                        holder.currentAnimator = null
                    }
                })
            }
            holder.currentAnimator = animator
            animator.start()
        } else {
            // EXPAND
            inflateServerCards(serverList, section)
            if (serverList.childCount == 0) {
                binding.ivExpandArrow.rotation = 0f
                return
            }

            // Measure real serverList height directly
            serverList.measure(
                View.MeasureSpec.makeMeasureSpec(binding.root.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetHeight = serverList.measuredHeight

            // Prepare: height 0, visible, cards fully visible (no stagger)
            serverList.visibility = View.VISIBLE
            (serverList.layoutParams as LinearLayout.LayoutParams).height = 0
            serverList.requestLayout()
            // Animate arrow rotation with a spring feel
            binding.ivExpandArrow.animate()
                .rotation(0f)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(1.5f))
                .start()

            val heightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 400
                interpolator = OvershootInterpolator(1.5f)
                addUpdateListener { anim ->
                    val fraction = anim.animatedValue as Float
                    val h = (targetHeight * fraction).toInt()
                    (serverList.layoutParams as LinearLayout.LayoutParams).height = h
                    serverList.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        (serverList.layoutParams as LinearLayout.LayoutParams).height = targetHeight
                        holder.currentAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        (serverList.layoutParams as LinearLayout.LayoutParams).height = targetHeight
                        holder.currentAnimator = null
                    }
                })
            }
            holder.currentAnimator = heightAnimator
            heightAnimator.start()
        }
    }

    private fun toggleGroup(section: Section) {
        val id = section.subscriptionId
        if (id in collapsedIds) collapsedIds.remove(id) else collapsedIds.add(id)
        saveCollapsedIds()
    }

    private fun inflateServerCards(container: LinearLayout, section: Section) {
        container.removeAllViews()
        val density = container.context.resources.displayMetrics.density

        for ((index, sc) in section.servers.withIndex()) {
            val cardView = if (serverCardStyle == "new") {
                val cardBinding = ItemRecyclerMainNewBinding.inflate(LayoutInflater.from(container.context), container, false)
                bindNewCardForGroup(cardBinding, sc)
                cardBinding.root
            } else {
                val cardBinding = ItemRecyclerMainBinding.inflate(LayoutInflater.from(container.context), container, false)
                bindClassicCardForGroup(cardBinding, sc)
                cardBinding.root
            }

            val materialCard = cardView as com.google.android.material.card.MaterialCardView
            val containerRadius = 16f * density
            val innerRadius = 4f * density
            val topRadius = if (index == 0) containerRadius else innerRadius
            val bottomRadius = if (index == section.servers.lastIndex) containerRadius else innerRadius
            materialCard.shapeAppearanceModel = materialCard.shapeAppearanceModel
                .toBuilder()
                .setTopLeftCornerSize(topRadius)
                .setTopRightCornerSize(topRadius)
                .setBottomLeftCornerSize(bottomRadius)
                .setBottomRightCornerSize(bottomRadius)
                .build()

            val lp = materialCard.layoutParams as ViewGroup.MarginLayoutParams
            val bottomMargin = if (index < section.servers.lastIndex) (4 * density).toInt() else 0
            lp.setMargins(0, 0, 0, bottomMargin)
            materialCard.layoutParams = lp

            cardView.setOnClickListener {
                it.performLightHapticFeedback()
                adapterListener?.onSelectServer(sc.guid)
            }

            cardView.setOnLongClickListener {
                adapterListener?.onShare(sc.guid, sc.profile, 0, true)
                true
            }

            container.addView(cardView)
        }
    }

    private fun bindClassicCardForGroup(binding: ItemRecyclerMainBinding, sc: ServersCache) {
        val profile = sc.profile
        val guid = sc.guid
        val context = binding.root.context

        binding.tvName.text = profile.remarks
        binding.tvStatistics.text = getAddress(profile)
        binding.tvType.text = getProtocolDescription(profile)

        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        binding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
        binding.tvTestResult.setTextColor(
            if ((aff?.testDelayMillis ?: 0L) < 0L)
                com.google.android.material.color.MaterialColors.getColor(
                    binding.tvTestResult, androidx.appcompat.R.attr.colorError
                )
            else
                com.google.android.material.color.MaterialColors.getColor(
                    binding.tvTestResult, com.google.android.material.R.attr.colorTertiary
                )
        )

        val isSelected = guid == MmkvManager.getSelectServer()
        if (isSelected) {
            binding.root.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.root, com.google.android.material.R.attr.colorSurfaceBright
                )
            )
            binding.root.cardElevation = 4f * context.resources.displayMetrics.density
        } else {
            binding.root.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.root, com.google.android.material.R.attr.colorSurfaceContainerHigh
                )
            )
            binding.root.cardElevation = 0f
        }

        binding.layoutSubscription.visibility = View.GONE
        binding.layoutShare.visibility = View.GONE
        binding.layoutEdit.visibility = View.GONE
        binding.layoutRemove.visibility = View.GONE
        binding.layoutMore.visibility = View.GONE

        binding.infoContainer.setOnClickListener {
            it.performLightHapticFeedback()
            adapterListener?.onSelectServer(guid)
        }
    }

    private fun bindNewCardForGroup(binding: ItemRecyclerMainNewBinding, sc: ServersCache) {
        val profile = sc.profile
        val guid = sc.guid
        val context = binding.root.context

        binding.tvName.text = profile.remarks
        binding.tvType.text = getProtocolDescription(profile)

        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        binding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
        binding.tvTestResult.setTextColor(
            if ((aff?.testDelayMillis ?: 0L) < 0L)
                com.google.android.material.color.MaterialColors.getColor(
                    binding.tvTestResult, androidx.appcompat.R.attr.colorError
                )
            else
                com.google.android.material.color.MaterialColors.getColor(
                    binding.tvTestResult, com.google.android.material.R.attr.colorTertiary
                )
        )

        val isSelected = guid == MmkvManager.getSelectServer()
        if (isSelected) {
            binding.root.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.root, com.google.android.material.R.attr.colorSurfaceBright
                )
            )
            binding.root.cardElevation = 4f * context.resources.displayMetrics.density
        } else {
            binding.root.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.root, com.google.android.material.R.attr.colorSurfaceContainerHigh
                )
            )
            binding.root.cardElevation = 0f
        }

        binding.layoutSubscription.visibility = View.GONE
        binding.btnMore.visibility = View.GONE

        binding.infoContainer.setOnClickListener {
            it.performLightHapticFeedback()
            adapterListener?.onSelectServer(guid)
        }
    }

    private fun bindClassicCard(holder: CardViewHolder, position: Int) {
        val (guid, profile) = getItemAt(position)
        val binding = holder.binding
        val context = binding.root.context
        val showIcons = showIcons()

        binding.tvName.text = profile.remarks
        binding.tvStatistics.text = getAddress(profile)
        binding.tvType.text = getProtocolDescription(profile)

        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        binding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
        binding.tvTestResult.setTextColor(
            if ((aff?.testDelayMillis ?: 0L) < 0L)
                com.google.android.material.color.MaterialColors.getColor(
                    binding.tvTestResult, androidx.appcompat.R.attr.colorError
                )
            else
                com.google.android.material.color.MaterialColors.getColor(
                    binding.tvTestResult, com.google.android.material.R.attr.colorTertiary
                )
        )

        val isSelected = guid == MmkvManager.getSelectServer()
        if (isSelected) {
            binding.root.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.root, com.google.android.material.R.attr.colorSurfaceBright
                )
            )
            binding.root.cardElevation = 4f * context.resources.displayMetrics.density
        } else {
            binding.root.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.root, com.google.android.material.R.attr.colorSurfaceContainerHigh
                )
            )
            binding.root.cardElevation = 0f
        }

        val subRemarks = getSubscriptionRemarks(profile)
        binding.tvSubscription.text = subRemarks
        binding.layoutSubscription.visibility = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE

        binding.layoutShare.visibility = if (showIcons) View.VISIBLE else View.GONE
        binding.layoutEdit.visibility = if (showIcons) View.VISIBLE else View.GONE
        binding.layoutRemove.visibility = if (showIcons) View.VISIBLE else View.GONE
        binding.layoutMore.visibility = if (showIcons) View.GONE else View.VISIBLE

        if (showIcons) {
            binding.layoutShare.setOnClickListener { adapterListener?.onShare(guid, profile, position, false) }
            binding.layoutEdit.setOnClickListener { adapterListener?.onEdit(guid, position, profile) }
            binding.layoutRemove.setOnClickListener { adapterListener?.onRemove(guid, position) }
        } else {
            binding.layoutMore.setOnClickListener { adapterListener?.onShare(guid, profile, position, true) }
        }

        binding.infoContainer.setOnClickListener {
            it.performLightHapticFeedback()
            adapterListener?.onSelectServer(guid)
        }
    }

    private fun bindNewCard(holder: NewCardViewHolder, position: Int) {
        val (guid, profile) = getItemAt(position)
        val binding = holder.binding
        val context = binding.root.context
        val showIcons = showIcons()

        binding.tvName.text = profile.remarks
        binding.tvType.text = getProtocolDescription(profile)

        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        binding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
        binding.tvTestResult.setTextColor(
            if ((aff?.testDelayMillis ?: 0L) < 0L)
                com.google.android.material.color.MaterialColors.getColor(
                    binding.tvTestResult, androidx.appcompat.R.attr.colorError
                )
            else
                com.google.android.material.color.MaterialColors.getColor(
                    binding.tvTestResult, com.google.android.material.R.attr.colorTertiary
                )
        )

        val isSelected = guid == MmkvManager.getSelectServer()
        if (isSelected) {
            binding.root.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.root, com.google.android.material.R.attr.colorSurfaceBright
                )
            )
            binding.root.cardElevation = 4f * context.resources.displayMetrics.density
        } else {
            binding.root.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.root, com.google.android.material.R.attr.colorSurfaceContainerHigh
                )
            )
            binding.root.cardElevation = 0f
        }

        val subRemarks = getSubscriptionRemarks(profile)
        binding.tvSubscription.text = subRemarks
        binding.layoutSubscription.visibility = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE

        if (showIcons) {
            binding.btnMore.visibility = View.VISIBLE
            binding.btnMore.setOnClickListener {
                val popup = android.widget.PopupMenu(context, it)
                popup.menuInflater.inflate(R.menu.menu_server_overflow, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_share -> { adapterListener?.onShare(guid, profile, position, false); true }
                        R.id.action_edit -> { adapterListener?.onEdit(guid, position, profile); true }
                        R.id.action_delete -> { adapterListener?.onRemove(guid, position); true }
                        else -> false
                    }
                }
                popup.show()
            }
        } else {
            binding.btnMore.visibility = View.GONE
        }

        binding.infoContainer.setOnClickListener {
            it.performLightHapticFeedback()
            adapterListener?.onSelectServer(guid)
        }
    }

    private fun getAddress(profile: ProfileItem): String {
        if (profile.configType == EConfigType.OLCRTC) {
            return com.v2ray.ang.core.OlcrtcManager.providerUrl(
                profile.olcrtcCarrier.orEmpty(),
                profile.olcrtcRoomId,
                profile.olcrtcServerUrl
            )
        }
        return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
    }

    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        if (mainViewModel.subscriptionId.isEmpty() && profile.subscriptionId.isNotEmpty()) {
            return MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks.orEmpty()
        }
        return ""
    }

    private fun getProtocolDescription(profile: ProfileItem): String {
        if (profile.configType.isComplexType()) return profile.configType.name
        if (profile.configType == EConfigType.OLCRTC) {
            val parts = mutableListOf("olcRTC")
            profile.olcrtcCarrier?.let { parts.add(it) }
            profile.olcrtcTransport?.let { parts.add(it) }
            return parts.joinToString(" / ")
        }
        val parts = mutableListOf(profile.configType.name)
        profile.network?.let { net ->
            if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) parts.add(net)
        }
        profile.security?.let { sec ->
            if (sec.isNotBlank()) {
                if (profile.insecure == true && sec.equals("tls", ignoreCase = true))
                    parts.add("$sec insecure")
                else
                    parts.add(sec)
            }
        }
        return parts.joinToString(" / ")
    }

    fun removeServerSub(guid: String, position: Int) {
        if (isGrouped) {
            var changed = false
            for (i in sections.indices) {
                val idx = sections[i].servers.indexOfFirst { it.guid == guid }
                if (idx >= 0) {
                    val list = sections[i].servers.toMutableList()
                    list.removeAt(idx)
                    sections[i] = sections[i].copy(servers = list)
                    changed = true
                    notifyItemChanged(i)
                    break
                }
            }
            if (!changed) notifyDataSetChanged()
        } else {
            val idx = flatData.indexOfFirst { it.guid == guid }
            if (idx >= 0) {
                flatData.removeAt(idx)
                notifyItemRemoved(idx)
                notifyItemRangeChanged(idx, flatData.size - idx)
            }
        }
    }

    fun setSelectServer(fromGuid: String, toGuid: String) {
        if (isGrouped) {
            notifyDataSetChanged()
            return
        }
        val fromPosition = mainViewModel.getPosition(fromGuid)
        val toPosition = mainViewModel.getPosition(toGuid)
        notifyItemChanged(fromPosition)
        notifyItemChanged(toPosition)
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (isGrouped) return false
        mainViewModel.swapServer(fromPosition, toPosition)
        if (fromPosition < flatData.size && toPosition < flatData.size) {
            Collections.swap(flatData, fromPosition, toPosition)
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {}
    override fun onItemDismiss(position: Int) {}

    class GroupContainerViewHolder(val binding: ItemGroupContainerBinding) : RecyclerView.ViewHolder(binding.root) {
        var currentAnimator: Animator? = null
    }
    class CardViewHolder(val binding: ItemRecyclerMainBinding) : RecyclerView.ViewHolder(binding.root)
    class NewCardViewHolder(val binding: ItemRecyclerMainNewBinding) : RecyclerView.ViewHolder(binding.root)
}
