package com.example.wfappops

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.auth.data.LegacyDemoCleanup
import com.example.wfappops.auth.data.isLegacyDemoFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccountStartupTest {
    @Test
    fun removesLegacyDemoArtifactsFromInstalledApp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        LegacyDemoCleanup(app).run()
        assertFalse(app.noBackupFilesDir.listFiles().orEmpty().any { isLegacyDemoFile(it.name) })
        assertFalse(app.getSharedPreferences("ready-product-notifications", 0).all.keys.any { it.startsWith("demo-") })
    }

    @Test
    fun installedAppDoesNotBundleTheAlternativeLoginAccount() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(app.assets.list("auth").orEmpty().contains("team-context.json"))
    }

    @Test
    fun localDraftOrderActivityIsEnabledAndNotExported() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(app.packageName, "${app.packageName}.orders.view.CreateOrderActivity")
        val info = app.packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertEquals(true, info.enabled)
        assertFalse(info.exported)
    }
}
