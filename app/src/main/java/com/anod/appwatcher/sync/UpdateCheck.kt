package com.anod.appwatcher.sync

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.os.RemoteException
import android.provider.BaseColumns
import android.text.TextUtils
import android.text.format.DateUtils
import androidx.core.content.contentValuesOf
import androidx.work.Data
import com.android.volley.VolleyError
import com.anod.appwatcher.Application
import com.anod.appwatcher.accounts.AuthTokenBlocking
import com.anod.appwatcher.backup.gdrive.GDriveSilentSignIn
import com.anod.appwatcher.backup.gdrive.GDriveSync
import com.anod.appwatcher.database.*
import com.anod.appwatcher.database.entities.App
import com.anod.appwatcher.database.entities.AppChange
import com.anod.appwatcher.database.entities.AppListItem
import com.anod.appwatcher.model.AppInfo
import com.anod.appwatcher.model.AppInfoMetadata
import com.anod.appwatcher.preferences.Preferences
import com.anod.appwatcher.utils.extractUploadDate
import finsky.api.BulkDocId
import finsky.api.model.DfeModel
import finsky.api.model.Document
import info.anodsplace.framework.AppLog
import info.anodsplace.framework.app.ApplicationContext
import info.anodsplace.framework.content.InstalledApps
import info.anodsplace.playstore.BulkDetailsEndpoint
import info.anodsplace.playstore.PlayStoreEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 *  @author alex
 *  @date 6/3/2017
 */

class UpdateCheck(private val context: ApplicationContext) : PlayStoreEndpoint.Listener {

    class UpdatedApp(
            val packageName: String,
            val versionNumber: Int,
            val title: String,
            val uploadTime: Long,
            val uploadDate: String,
            val recentChanges: String,
            val installedVersionCode: Int,
            val isNewUpdate: Boolean) {

        var noNewDetails = false

        constructor(appInfo: AppInfo, recentChanges: String, installedVersionCode: Int, isNewUpdate: Boolean)
                : this(appInfo.packageName, appInfo.versionNumber, appInfo.title, appInfo.uploadTime, appInfo.uploadDate, recentChanges, installedVersionCode, isNewUpdate)

        constructor(app: App, recentChanges: String, installedVersionCode: Int, isNewUpdate: Boolean)
                : this(app.packageName, app.versionNumber, app.title, app.uploadTime, app.uploadDate, recentChanges, installedVersionCode, isNewUpdate)
    }

    companion object {
        private const val oneSecInMillis = 1000
        private const val bulkSize = 20
        internal const val extrasManual = "manual"

        const val syncStop = "com.anod.appwatcher.sync.start"
        const val syncProgress = "com.anod.appwatcher.sync.progress"
        const val extrasUpdatesCount = "extra_updates_count"
    }

    private val preferences = Application.provide(context).prefs
    private val installedAppsProvider = InstalledApps.PackageManager(context.packageManager)

    suspend fun perform(extras: Data): Int = withContext(Dispatchers.Default) {

        val manualSync = extras.getBoolean(extrasManual, false)
        AppLog.i("Perform ${if (manualSync) "manual" else "scheduled"} sync", "UpdateCheck")
        // Skip any check if sync requested from application
        if (!manualSync) {
            if (preferences.isWifiOnly && !Application.provide(context).networkConnection.isWifiEnabled) {
                AppLog.i("Wifi not enabled, skipping update check....", "UpdateCheck")
                return@withContext -1
            }
            val updateTime = preferences.lastUpdateTime
            if (updateTime != (-1).toLong() && System.currentTimeMillis() - updateTime < oneSecInMillis) {
                AppLog.i("Last update less than second, skipping...", "UpdateCheck")
                return@withContext -1
            }
        }
        val account = preferences.account
        if (account == null) {
            AppLog.w("No active account, skipping sync...", "UpdateCheck")
            return@withContext -1
        }

        if (!Application.provide(context).networkConnection.isNetworkAvailable) {
            AppLog.w("Network is not available, skipping sync...", "UpdateCheck")
            return@withContext -1
        }

        AppLog.i("Perform synchronization", "UpdateCheck")

        val authToken = requestAuthToken(account)
        if (authToken == null) {
            AppLog.e("Cannot receive access token")
            return@withContext -1
        }

        //Broadcast progress intent
        val startIntent = Intent(syncProgress)
        context.sendBroadcast(startIntent)

        val lastUpdatesViewed = preferences.isLastUpdatesViewed
        AppLog.d("Last update viewed: $lastUpdatesViewed")

        var updatedApps: List<UpdatedApp> = emptyList()
        try {
            updatedApps = doSync(lastUpdatesViewed, authToken, account)
        } catch (e: RemoteException) {
            AppLog.e("Error during synchronization ${e.message}", e)
        }

        if (updatedApps.isNotEmpty() && updatedApps.first().uploadTime == 0.toLong()) {
            val uploadDate = updatedApps.first().uploadDate
            val locale = Locale.getDefault()
            AppLog.e("Cannot parse date '$uploadDate' for locale '$locale'")
        }

        val now = System.currentTimeMillis()
        preferences.lastUpdateTime = now

        if (!manualSync
                && updatedApps.isNotEmpty()
                && (updatedApps.firstOrNull { it.isNewUpdate } != null)
                && lastUpdatesViewed) {
            preferences.isLastUpdatesViewed = false
        }

        notifyIfNeeded(manualSync, updatedApps)

        if (!manualSync) {
            if (preferences.isDriveSyncEnabled) {
                AppLog.d("DriveSyncEnabled = true")
                performGDriveSync(preferences, now)
            } else {
                AppLog.d("DriveSyncEnabled = false, skipping...")
            }
        }

        AppLog.d("Finish::perform()")
        return@withContext updatedApps.size
    }

    override fun onDataChanged(data: DfeModel) {
    }

    override fun onErrorResponse(error: VolleyError) {
    }

    @Throws(RemoteException::class)
    private suspend fun doSync(lastUpdatesViewed: Boolean, authToken: String, account: Account): List<UpdatedApp> {

        val database = Application.provide(context).database

        val apps = AppListTable.Queries.loadAppList(database.apps())
        if (apps.isEmpty) {
            apps.close()
            AppLog.i("Sync finished: no apps", "UpdateCheck")
            return listOf()
        }

        val updatedApps = mutableListOf<UpdatedApp>()

        apps.chunked(bulkSize) { list ->
            list.associateBy { it.app.packageName }
        }.forEach { localApps ->
            val docIds = localApps.map { BulkDocId(it.key, it.value.app.versionNumber) }
            val endpoint = createEndpoint(docIds, authToken, account)
            AppLog.d("Sending chunk... $docIds")
            try {
                endpoint.startSync()
            } catch (e: VolleyError) {
                AppLog.e("Fetching of bulk updates failed ${e.message ?: ""}", "UpdateCheck")
            }
            AppLog.i("Sent ${docIds.size}, received ${endpoint.documents.size}", "UpdateCheck")
            updateApps(endpoint.documents, localApps, updatedApps, lastUpdatesViewed, context.contentResolver, database)
        }

        apps.close()
        AppLog.i("Sync finished for ${apps.count} apps", "UpdateCheck")
        return updatedApps
    }

    private suspend fun updateApps(documents: List<Document>, localApps: Map<String, AppListItem>, updatedApps: MutableList<UpdatedApp>, lastUpdatesViewed: Boolean, contentResolver: ContentResolver, db: AppsDatabase) {
        val fetched = mutableMapOf<String, Boolean>()
        val batch = mutableListOf<ContentValues>()
        val changelog = mutableListOf<ContentValues>()
        for (marketApp in documents) {
            val docId = marketApp.docId
            localApps[docId]?.let { localItem ->
                fetched[docId] = true
                val (values, updatedApp) = updateApp(marketApp, localItem, lastUpdatesViewed)
                if (values.size() > 0) {
                    batch.add(values)
                }
                val isNewVersion = marketApp.appDetails.versionCode > localItem.app.versionNumber
                val recentChanges = marketApp.appDetails.recentChangesHtml?.trim() ?: ""
                val noNewDetails = if (isNewVersion)
                    recentChanges == (localItem.changeDetails?.trim() ?: "")
                else
                    localItem.noNewDetails
                changelog.add(AppChange(
                        docId,
                        marketApp.appDetails.versionCode,
                        marketApp.appDetails.versionString,
                        recentChanges,
                        marketApp.appDetails.uploadDate,
                        noNewDetails).contentValues)
                if (updatedApp != null) {
                    updatedApp.noNewDetails = noNewDetails
                    updatedApps.add(updatedApp)
                }
            }
        }

        if (batch.isNotEmpty()) {
            db.applyBatchUpdates(contentResolver, batch) {
                val rowId = it.getAsString(BaseColumns._ID)
                DbContentProvider.appsUri.buildUpon().appendPath(rowId).build()
            }
        }

        if (changelog.isNotEmpty()) {
            db.applyBatchInsert(contentResolver, changelog) {
                DbContentProvider.changelogUri
                        .buildUpon()
                        .appendPath("apps")
                        .appendPath(it.getAsString(ChangelogTable.Columns.appId))
                        .appendPath("v")
                        .appendPath(it.getAsString(ChangelogTable.Columns.versionCode))
                        .build()
            }
        }

        // Reset not fetched app statuses
        if (lastUpdatesViewed && fetched.size < localApps.size) {
            val statusBatch = mutableListOf<ContentValues>()
            localApps.values.forEach {
                val app = it.app
                if (fetched[app.appId] == null) {
                    if (app.status == AppInfoMetadata.STATUS_UPDATED) {
                        AppLog.d("Set not fetched app as viewed")
                        statusBatch.add(contentValuesOf(
                                BaseColumns._ID to app.rowId,
                                AppListTable.Columns.status to AppInfoMetadata.STATUS_NORMAL
                        ))
                    }
                }
            }
            if (statusBatch.isNotEmpty()) {
                db.applyBatchUpdates(contentResolver, statusBatch) {
                    val rowId = it.getAsString(BaseColumns._ID)
                    DbContentProvider.appsUri.buildUpon().appendPath(rowId).build()
                }
            }
        }
    }

    private fun updateApp(marketApp: Document, localItem: AppListItem, lastUpdatesViewed: Boolean): Pair<ContentValues, UpdatedApp?> {
        val appDetails = marketApp.appDetails
        val localApp = localItem.app

        if (appDetails.versionCode > localApp.versionNumber) {
            AppLog.d("New version found [" + appDetails.versionCode + "]")
            val newApp = AppInfo(localApp.rowId, AppInfoMetadata.STATUS_UPDATED, marketApp)
            val installedInfo = installedAppsProvider.packageInfo(appDetails.packageName)
            val recentChanges = appDetails.recentChangesHtml ?: ""
            return Pair(newApp.contentValues, UpdatedApp(newApp, recentChanges, installedInfo.versionCode, true))
        }

        val values = ContentValues()
        var updatedApp: UpdatedApp? = null
        //Mark updated app as normal
        if (localApp.status == AppInfoMetadata.STATUS_UPDATED && lastUpdatesViewed) {
            AppLog.d("Set ${localApp.appId} update as viewed")
            values.put(AppListTable.Columns.status, AppInfoMetadata.STATUS_NORMAL)
        } else if (localApp.status == AppInfoMetadata.STATUS_UPDATED) {
            // Application was previously updated
            val installedInfo = installedAppsProvider.packageInfo(appDetails.packageName)
            val recentChanges = appDetails.recentChangesHtml ?: ""
            updatedApp = UpdatedApp(localApp, recentChanges, installedInfo.versionCode, false)
        }
        //Refresh app icon if it wasn't fetched previously
        fillMissingData(marketApp, localApp, values)
        if (values.size() > 0) {
            values.put(BaseColumns._ID, localApp.rowId)
        }
        return Pair(values, updatedApp)
    }

    private fun notifyIfNeeded(manualSync: Boolean, updatedApps: List<UpdatedApp>) {
        val sn = SyncNotification(context)
        if (manualSync) {
            if (updatedApps.isEmpty()) {
                AppLog.i("No new updates", "UpdateCheck")
            } else {
                AppLog.i("Updates: [${updatedApps.joinToString(",") { "${it.title} (${it.versionNumber})" }}]", "UpdateCheck")
            }
            sn.cancel()
        } else if (updatedApps.isNotEmpty()) {
            val hasFilters = (!preferences.isNotifyInstalled)
                    || (!preferences.isNotifyInstalledUpToDate)
            val filteredApps = if (hasFilters) {
                updatedApps.filter append@{
                    if (!preferences.isNotifyInstalled) {
                        if (it.installedVersionCode <= 0) {
                            return@append false
                        }
                    } else if (!preferences.isNotifyInstalledUpToDate) {
                        if (!(it.installedVersionCode > 0 && it.versionNumber <= it.installedVersionCode)) {
                            return@append false
                        }
                    }
                    if (!preferences.isNotifyNoChanges) {
                        if (it.noNewDetails) {
                            return@append false
                        }
                    }
                    true
                }
            } else {
                updatedApps
            }
            AppLog.i("Notifying about: [${filteredApps.joinToString(",") { "${it.title} (${it.versionNumber})" }}]", "UpdateCheck")
            if (filteredApps.isNotEmpty()) {
                sn.show(filteredApps)
            }
        } else {
            AppLog.i("No new updates", "UpdateCheck")
        }
    }

    private suspend fun performGDriveSync(pref: Preferences, now: Long) {
        val driveSyncTime = pref.lastDriveSyncTime
        if (driveSyncTime == (-1).toLong() || now > DateUtils.DAY_IN_MILLIS + driveSyncTime) {
            AppLog.d("DriveSync perform sync")
            val signIn = GDriveSilentSignIn(context)

            try {
                AppLog.i("Perform Google Drive sync", "UpdateCheck")
                val googleAccount = signIn.signInLocked()
                val worker = GDriveSync(context, googleAccount)
                worker.doSync()
                pref.lastDriveSyncTime = System.currentTimeMillis()
            } catch (e: Exception) {
                AppLog.e("Perform Google Drive sync exception: ${e.message
                        ?: "'empty message'"}", e)
                AppLog.e("Google Drive sync exception: ${e.message ?: "'empty message'"}")
            }
        } else {
            AppLog.d("DriveSync backup is fresh")
        }
    }

    private suspend fun requestAuthToken(account: Account): String? {
        val tokenHelper = AuthTokenBlocking(context)
        var authToken: String? = null
        try {
            authToken = tokenHelper.request(null, account)
        } catch (e: Throwable) {
            AppLog.e("AuthTokenBlocking request exception: " + e.message, e)
        }

        return authToken
    }

    private fun createEndpoint(docIds: List<BulkDocId>, authToken: String, account: Account): BulkDetailsEndpoint {
        val endpoint = BulkDetailsEndpoint(context.actual, Application.provide(context).requestQueue, Application.provide(context).deviceInfo, account, docIds)
        endpoint.authToken = authToken
        endpoint.listener = this
        return endpoint
    }

    private fun fillMissingData(marketApp: Document, localApp: App, values: ContentValues) {
        val refreshTime = marketApp.extractUploadDate()
        values.put(AppListTable.Columns.uploadTimestamp, refreshTime)
        values.put(AppListTable.Columns.uploadDate, marketApp.appDetails.uploadDate)
        if (TextUtils.isEmpty(localApp.versionName)) {
            values.put(AppListTable.Columns.versionName, marketApp.appDetails.versionString)
        }

        if (marketApp.appDetails.appType != localApp.appType) {
            values.put(AppListTable.Columns.appType, marketApp.appDetails.appType)
        }

        val offer = marketApp.offer
        if (offer.currencyCode != localApp.price.cur) {
            values.put(AppListTable.Columns.priceCurrency, offer.currencyCode)
        }
        if (offer.formattedAmount != localApp.price.text) {
            values.put(AppListTable.Columns.priceText, offer.formattedAmount)
        }
        if (localApp.price.micros != offer.micros.toInt()) {
            values.put(AppListTable.Columns.priceMicros, offer.micros)
        }

        val iconUrl = marketApp.iconUrl
        if (!TextUtils.isEmpty(iconUrl)) {
            values.put(AppListTable.Columns.iconUrl, marketApp.iconUrl)
        }
    }
}