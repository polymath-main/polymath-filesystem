package com.polymath.fs.ui.volumes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.polymath.fs.R
import com.polymath.fs.data.db.entities.SafGrantEntity
import com.polymath.fs.data.remote.RemoteProfileEntity
import com.polymath.fs.domain.vfs.VfsUri

val SafGrantEntity.treeUriString: String
    get() = treeUri

fun RemoteProfileEntity.toVfsUri(): VfsUri {
    val cleanPath = when {
        defaultPath.isBlank() -> "/"
        defaultPath.startsWith("/") -> defaultPath
        else -> "/$defaultPath"
    }
    return VfsUri(
        scheme = scheme.ifBlank { "sftp" },
        authority = id,
        path = cleanPath
    )
}

sealed class VolumeItem {
    data class Header(val title: String) : VolumeItem()

    data class LocalStorage(
        val title: String,
        val path: String,
        val iconRes: Int
    ) : VolumeItem()

    data class SafGrant(
        val grant: SafGrantEntity
    ) : VolumeItem()

    data class RemoteProfile(
        val profile: RemoteProfileEntity
    ) : VolumeItem()

    data class Action(
        val title: String,
        val isSaf: Boolean
    ) : VolumeItem()
}

class VolumesAdapter(
    private val onNavigate: (path: String) -> Unit,
    private val onDeleteSaf: (treeUriString: String) -> Unit,
    private val onDeleteRemote: (profileId: String) -> Unit,
    private val onAddSafClick: () -> Unit,
    private val onAddSftpClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_VOLUME = 1
        private const val TYPE_ACTION = 2
    }

    private val items = mutableListOf<VolumeItem>()

    fun updateData(
        safGrants: List<SafGrantEntity>,
        remoteProfiles: List<RemoteProfileEntity>
    ) {
        val newItems = mutableListOf<VolumeItem>()

        // 1. Local Storage
        newItems.add(VolumeItem.Header("Local Storage"))
        newItems.add(
            VolumeItem.LocalStorage(
                title = "Root (/)",
                path = "/",
                iconRes = R.drawable.ic_fluent_system
            )
        )
        newItems.add(
            VolumeItem.LocalStorage(
                title = "Primary Storage",
                path = "/storage/emulated/0",
                iconRes = R.drawable.ic_fluent_folder
            )
        )

        // 2. Granted SAF Trees
        newItems.add(VolumeItem.Header("Granted SAF Trees"))
        for (grant in safGrants) {
            newItems.add(VolumeItem.SafGrant(grant))
        }
        newItems.add(VolumeItem.Action("+ Add Storage (SAF)", isSaf = true))

        // 3. Remote SFTP Profiles
        newItems.add(VolumeItem.Header("Remote SFTP Profiles"))
        for (profile in remoteProfiles) {
            newItems.add(VolumeItem.RemoteProfile(profile))
        }
        newItems.add(VolumeItem.Action("+ Add SFTP Server", isSaf = false))

        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is VolumeItem.Header -> TYPE_HEADER
            is VolumeItem.Action -> TYPE_ACTION
            else -> TYPE_VOLUME
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_volume_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_ACTION -> {
                val view = inflater.inflate(R.layout.item_volume_action, parent, false)
                ActionViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_volume, parent, false)
                VolumeViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is VolumeItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is VolumeItem.Action -> (holder as ActionViewHolder).bind(item)
            is VolumeItem.LocalStorage -> (holder as VolumeViewHolder).bindLocal(item)
            is VolumeItem.SafGrant -> (holder as VolumeViewHolder).bindSaf(item.grant)
            is VolumeItem.RemoteProfile -> (holder as VolumeViewHolder).bindRemote(item.profile)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvHeaderTitle)

        fun bind(title: String) {
            tvTitle.text = title
        }
    }

    inner class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardAction: MaterialCardView = itemView.findViewById(R.id.cardAction)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvActionTitle)

        fun bind(action: VolumeItem.Action) {
            tvTitle.text = action.title
            cardAction.setOnClickListener {
                if (action.isSaf) {
                    onAddSafClick()
                } else {
                    onAddSftpClick()
                }
            }
        }
    }

    inner class VolumeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardVolume: MaterialCardView = itemView.findViewById(R.id.cardVolume)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivVolumeIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvVolumeTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvVolumeSubtitle)
        private val tvSecurityBadge: TextView? = itemView.findViewById(R.id.tvVolumeSecurityBadge)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteVolume)

        fun bindLocal(item: VolumeItem.LocalStorage) {
            ivIcon.setImageResource(item.iconRes)
            if (item.path == "/") {
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#f59e0b"))
                tvSecurityBadge?.text = "ROOT FS"
                tvSecurityBadge?.setTextColor(android.graphics.Color.parseColor("#f59e0b"))
            } else {
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#38bdf8"))
                tvSecurityBadge?.text = "FLASH"
                tvSecurityBadge?.setTextColor(android.graphics.Color.parseColor("#38bdf8"))
            }
            tvSecurityBadge?.setBackgroundResource(R.drawable.bg_badge_inactive)
            tvSecurityBadge?.visibility = View.VISIBLE
            tvTitle.text = item.title
            tvSubtitle.text = item.path
            btnDelete.visibility = View.GONE
            cardVolume.setOnClickListener {
                onNavigate(item.path)
            }
        }

        fun bindSaf(grant: SafGrantEntity) {
            ivIcon.setImageResource(R.drawable.ic_fluent_folder)
            ivIcon.setColorFilter(android.graphics.Color.parseColor("#34d399"))
            tvTitle.text = grant.label.ifBlank { "SAF Volume" }
            tvSubtitle.text = grant.treeDocId.ifBlank { grant.treeUriString }
            tvSecurityBadge?.text = "SAF TREE"
            tvSecurityBadge?.setTextColor(android.graphics.Color.parseColor("#34d399"))
            tvSecurityBadge?.setBackgroundResource(R.drawable.bg_badge_inactive)
            tvSecurityBadge?.visibility = View.VISIBLE
            btnDelete.visibility = View.VISIBLE
            cardVolume.setOnClickListener {
                onNavigate(grant.treeUriString)
            }
            btnDelete.setOnClickListener {
                onDeleteSaf(grant.treeUriString)
            }
        }

        fun bindRemote(profile: RemoteProfileEntity) {
            ivIcon.setImageResource(R.drawable.ic_workspace_snapshot)
            ivIcon.setColorFilter(android.graphics.Color.parseColor("#a78bfa"))
            val displayTitle = if (profile.label.isNotBlank()) profile.label else "${profile.username}@${profile.host}"
            val displaySubtitle = "${profile.username}@${profile.host}:${profile.port}${if (profile.defaultPath.startsWith("/")) profile.defaultPath else "/${profile.defaultPath}"}"
            tvTitle.text = displayTitle
            tvSubtitle.text = displaySubtitle
            tvSecurityBadge?.text = if (profile.authType == "key") "AES-256 • KEY" else "AES-256 • PWD"
            tvSecurityBadge?.setTextColor(android.graphics.Color.parseColor("#a78bfa"))
            tvSecurityBadge?.setBackgroundResource(R.drawable.bg_badge_inactive)
            tvSecurityBadge?.visibility = View.VISIBLE
            btnDelete.visibility = View.VISIBLE
            cardVolume.setOnClickListener {
                onNavigate(profile.toVfsUri().serialized())
            }
            btnDelete.setOnClickListener {
                onDeleteRemote(profile.id)
            }
        }
    }
}
