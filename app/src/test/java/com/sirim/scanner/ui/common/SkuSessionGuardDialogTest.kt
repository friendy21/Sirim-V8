package com.sirim.scanner.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkuSessionGuardDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsPromptWhenSessionMissing() {
        composeRule.setContent {
            SkuSessionGuardDialog(
                state = SkuSessionGuardState.PromptCapture(
                    onDismiss = {},
                    onOpenSkuScanner = {}
                )
            )
        }

        composeRule.onNodeWithText("Select a SKU first").assertIsDisplayed()
        composeRule.onNodeWithText("Capture a SKU barcode before recording OCR data so the results can be linked correctly.")
            .assertIsDisplayed()
    }
}
