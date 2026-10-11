package dev.technics.pods.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.app.Application
import dev.technics.pods.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recreationRetainsOneStateOwnerAndSelectedTab() {
        lateinit var owner: TechnicsPodsViewModel
        compose.activityRule.scenario.onActivity { owner = ViewModelProvider(it)[TechnicsPodsViewModel::class.java] }
        val originalAppearance = owner.state.value.appearance
        try {
            compose.onNodeWithTag("tab_2").performClick()
            compose.onNodeWithTag("appearance_setting").performClick()
            compose.onNodeWithTag("theme_Dark").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("page_2").assertIsDisplayed()
            compose.onNodeWithTag("appearance_Dark").assertIsDisplayed()
            compose.activityRule.scenario.onActivity {
                val restored = ViewModelProvider(it)[TechnicsPodsViewModel::class.java]
                assertSame(owner, restored)
                assertEquals(2, restored.state.value.selectedTab)
                assertEquals(AppAppearance.Dark, restored.state.value.appearance)
            }
        } finally {
            compose.activityRule.scenario.onActivity {
                ViewModelProvider(it)[TechnicsPodsViewModel::class.java].setAppearance(originalAppearance)
            }
        }
    }

    @Test fun newStateOwnerRestoresSavedTabAndPendingPermissionAction() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val initialHandle = SavedStateHandle()
        val initial = TechnicsPodsViewModel(app, initialHandle)
        val firstStore = ViewModelStore().apply { put("model", initial) }
        initial.selectTab(2)
        initial.rememberPermissionAction(UiAction.ChooseDevice)
        val values = initialHandle.keys().associateWith { initialHandle.get<Any>(it) }
        firstStore.clear()
        val restored = TechnicsPodsViewModel(app, SavedStateHandle(values))
        val restoredStore = ViewModelStore().apply { put("model", restored) }
        try {
            assertNotSame(initial, restored)
            assertEquals(2, restored.state.value.selectedTab)
            assertEquals(UiAction.ChooseDevice, restored.consumePermissionAction())
            assertEquals(null, restored.consumePermissionAction())
        } finally { restoredStore.clear() }
    }
}
