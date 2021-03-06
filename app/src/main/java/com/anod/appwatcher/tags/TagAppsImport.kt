package com.anod.appwatcher.tags

import com.anod.appwatcher.Application
import com.anod.appwatcher.database.AppTagsTable
import com.anod.appwatcher.database.entities.AppTag
import com.anod.appwatcher.database.entities.Tag
import info.anodsplace.framework.app.ApplicationContext

internal class TagAppsImport(private val tag: Tag, private val context: ApplicationContext) {

    private var apps = mutableMapOf<String, Boolean>()
    private var defaultSelected: Boolean = false

    fun selectAll(select: Boolean) {
        apps = mutableMapOf()
        defaultSelected = select
    }

    fun updateApp(appId: String, checked: Boolean) {
        apps[appId] = checked
    }

    fun isSelected(appId: String): Boolean {
        if (apps.containsKey(appId)) {
            return apps[appId] ?: false
        }
        return defaultSelected
    }

    fun initSelected(list: List<AppTag> ) {
        if (list.isEmpty()) {
            return
        }
        this.apps = list.map { it.appId to true }.toMap().toMutableMap()
    }

    suspend fun run(): Boolean {
        val appIds = apps.filter { it.value }.keys.toList()

        val db = Application.provide(context).database
        AppTagsTable.Queries.assignAppsToTag(appIds, tag.id, db)
        return true
    }
}