package dev.sadeghtahani.rhinochat.testing

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner

/** The fixture starts Koin before MainActivity, so the production database is never opened. */
class ChatUiTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, ChatUiTestApplication::class.java.name, context)
}

/** Activity restoration may precede JUnit; even startup must have an isolated, usable graph. */
class ChatUiTestApplication : Application() {
    private var bootstrap: ChatUiFixture? = null

    override fun onCreate() {
        super.onCreate()
        bootstrap = ChatUiFixture().also { it.start(this) }
    }

    internal fun finishBootstrap() {
        val initial = bootstrap ?: return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
        }
        instrumentation.waitForIdleSync()
        initial.close()
        bootstrap = null
    }
}
