package com.anod.appwatcher.preferences

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.anod.appwatcher.backup.ExportTask
import com.anod.appwatcher.backup.ImportTask
import info.anodsplace.framework.app.ApplicationContext
import kotlinx.coroutines.flow.flow

class SettingsViewModel(application: Application): AndroidViewModel(application) {

    fun import(srcUri: Uri) = flow {
        emit(-1)
        emit(ImportTask(ApplicationContext(getApplication())).execute(srcUri))
        emit(-1)
    }

    fun export(dstUri: Uri) = flow {
        emit(-1)
        emit(ExportTask(ApplicationContext(getApplication())).execute(dstUri))
    }

}