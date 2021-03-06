package com.anod.appwatcher.details

import android.content.Context
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anod.appwatcher.R
import com.anod.appwatcher.database.entities.AppChange
import info.anodsplace.framework.text.Html

class ChangeView(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val changelog: TextView = itemView.findViewById<TextView>(R.id.changelog).apply {
        autoLinkMask = Linkify.ALL
    }
    val version: TextView = itemView.findViewById(R.id.version)
    val uploadDate: TextView = itemView.findViewById(R.id.upload_date)

    fun bindView(change: AppChange) {
        version.text = "${change.versionName} (${change.versionCode})"
        uploadDate.text = change.uploadDate
        if (change.details.isEmpty()) {
            changelog.setText(R.string.no_recent_changes)
        } else {
            changelog.text = Html.parse(change.details)
        }
    }
}

class ChangesAdapter(private val context: Context) : RecyclerView.Adapter<ChangeView>() {

    private var localChanges = emptyList<AppChange>()

    val isEmpty: Boolean
        get() = itemCount == 0

    override fun getItemCount() = localChanges.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChangeView {
        val v = LayoutInflater.from(context).inflate(R.layout.list_item_change, parent, false)
        return ChangeView(v)
    }

    override fun onBindViewHolder(holder: ChangeView, position: Int) {
        holder.bindView(localChanges[position])
    }

    fun setData(localChanges: List<AppChange>, recentChange: AppChange) {
        when {
            localChanges.isEmpty() -> {
                if (!recentChange.isEmpty) {
                    this.localChanges = listOf(recentChange)
                }
            }
            localChanges.first().versionCode == recentChange.versionCode -> {
                this.localChanges = listOf(recentChange, *localChanges.subList(1, localChanges.size).toTypedArray())
            }
            else -> {
                if (recentChange.isEmpty) {
                    this.localChanges = localChanges
                } else {
                    this.localChanges = listOf(recentChange, *localChanges.toTypedArray())
                }
            }
        }
        notifyDataSetChanged()
    }
}