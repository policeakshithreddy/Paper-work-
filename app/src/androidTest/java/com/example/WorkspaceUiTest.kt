package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class WorkspaceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigateSearchAndChangeAppearance() {
        compose.onNodeWithText("Scan a document").assertIsDisplayed()
        compose.onNodeWithContentDescription("Documents").performClick()
        compose.onNodeWithText("Search your documents").performTextInput("receipt")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Files you own").assertExists()
        compose.onNodeWithText("System").performClick()
    }
}
