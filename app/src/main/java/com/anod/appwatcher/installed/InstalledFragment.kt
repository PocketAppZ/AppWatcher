// Copyright (c) 2020. Alex Gavrishev
package com.anod.appwatcher.installed

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.paging.PagingSource
import com.anod.appwatcher.Application
import com.anod.appwatcher.R
import com.anod.appwatcher.database.AppsDatabase
import com.anod.appwatcher.database.entities.AppListItem
import com.anod.appwatcher.database.entities.packageToApp
import com.anod.appwatcher.model.Filters
import com.anod.appwatcher.watchlist.*
import info.anodsplace.framework.app.ApplicationContext
import info.anodsplace.framework.app.CustomThemeColors
import info.anodsplace.framework.app.FragmentFactory
import info.anodsplace.framework.app.FragmentToolbarActivity

class InstalledPagingSource(
        private val sortId: Int,
        private val titleFilter: String,
        private val appContext: ApplicationContext
) : PagingSource<Int, SectionItem>() {
    private val database: AppsDatabase = Application.provide(appContext).database

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SectionItem> {
        val installed = InstalledTaskWorker(appContext, sortId, titleFilter).run()
        val allInstalledPackageNames = installed.map { it.packageName }
        val watchingPackages = database.apps().loadRowIds(allInstalledPackageNames).associateBy({ it.packageName }, { it.rowId })
        val items: List<SectionItem> = allInstalledPackageNames
                .asSequence()
                .map { appContext.packageManager.packageToApp(watchingPackages[it] ?: -1, it) }
                .map { app -> OnDeviceItem(AppListItem(app, "", noNewDetails = false, recentFlag = false)) }
                .toList()

        return LoadResult.Page(
                data = items,
                prevKey = null,
                nextKey = null
        )
    }

}

class RecentlyInstalledViewModel(application: android.app.Application) : WatchListViewModel(application) {

    override fun createPagingSource(config: WatchListPagingSource.Config): PagingSource<Int, SectionItem> {
        return InstalledPagingSource(sortId, titleFilter, context)
    }

    override fun createSectionHeaderFactory(config: WatchListPagingSource.Config) = SectionHeaderFactory.Empty()
}

class InstalledFragment : WatchListFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.installed, menu)
    }

    override fun viewModelFactory(): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T = RecentlyInstalledViewModel(application) as T
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.installed)
    }

    class Factory(
            private val sortId: Int,
            private val showImportAction: Boolean
    ) : FragmentFactory("recently-installed-$sortId-$showImportAction") {

        override fun create(): Fragment? = InstalledFragment().also {
            it.arguments = Bundle().apply {
                putInt(ARG_FILTER, Filters.TAB_ALL)
                putInt(ARG_SORT, sortId)
                putBoolean(ARG_SHOW_ACTION, showImportAction)
            }
        }
    }

    companion object {
        fun intent(sortId: Int, showImportAction: Boolean, context: Context, themeRes: Int, themeColors: CustomThemeColors) = FragmentToolbarActivity.intent(
                Factory(sortId, showImportAction),
                Bundle.EMPTY,
                themeRes,
                themeColors,
                context)
    }
}