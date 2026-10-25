package dev.sadeghtahani.rhinochat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkManagerIsolationTest {
    @Test fun testManagerUsesSeparatePackageAndMemoryDatabaseWithExplicitDelayControl() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("dev.sadeghtahani.rhinochat.uitest", context.packageName)
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        val manager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<OutgoingMessageRetryWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        manager.enqueue(request).result.get(10, TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.ENQUEUED, requireNotNull(manager.getWorkInfoById(request.id).get()).state)
        // Invalid input deliberately fails before any repository lookup. Only this test request
        // runs, and only after the test driver releases its delay.
        driver.setInitialDelayMet(request.id)
        val finished = withTimeout(10_000) { manager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished == true } }
        assertEquals(WorkInfo.State.FAILED, finished?.state)
        assertFalse(context.getDatabasePath("androidx.work.workdb").exists())
        assertTrue(context.applicationContext is dev.sadeghtahani.rhinochat.testing.ChatUiTestApplication)
    }
}
