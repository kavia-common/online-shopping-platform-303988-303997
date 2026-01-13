package com.example.kotlinfrontend.data

import android.content.Context

/**
 * Simple repositories container used by Activities/ViewModels.
 */
class AppRepositories(appContext: Context) {

    private val appContext = appContext.applicationContext

    val notificationsStore: NotificationsStore = NotificationsStore(this.appContext)
    val notificationsRepository: NotificationsRepository =
        NotificationsRepository(this.appContext, notificationsStore)

    val demoNotificationsEngine: DemoNotificationsEngine =
        DemoNotificationsEngine(this.appContext, notificationsRepository, notificationsStore)

    // Other repositories are already present in the project; this file is kept minimal here.
}
