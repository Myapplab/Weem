package com.kuryelog.app.map

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.Poi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Yer düzeltme penceresi (MAP.md 5.3 `poi_editor`): başlık, tür seçici, ad boşsa Kaydet pasif, kayıt adı büyük harfli. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PoiEditDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun yeniIsletmeEklemeKaydetPasifSonraAdVeTur() {
        var saved: Pair<String, Char>? = null
        compose.setContent { WeemTheme(false) { PoiEditDialog(null, Lang.TR, onSave = { n, k -> saved = n to k }, onDismiss = {}) } }
        compose.onAllNodes(hasText("İşletme ekle")).assertCountEquals(1)
        compose.onNodeWithText("Kaydet").assertIsNotEnabled()
        compose.onNodeWithTag("poi-name").performTextInput("izmir doner")
        compose.onNodeWithText("Kafe").performClick()
        compose.onNodeWithText("Kaydet").assertIsEnabled().performClick()
        assertEquals("İzmir Doner" to 'c', saved)
    }

    @Test
    fun adDuzeltmeMevcutAdlaAcilirAzericeNot() {
        compose.setContent { WeemTheme(false) { PoiEditDialog(Poi(40.4, 49.8, 'f', "Doner", "node/1"), Lang.AZ, onSave = { _, _ -> }, onDismiss = {}) } }
        compose.onAllNodes(hasText("Obyektin adını düzəlt")).assertCountEquals(1)
        compose.onAllNodes(hasText("Bu düzəliş WEEM-də qalır; xəritə yenilənsə də itmir.")).assertCountEquals(1)
        compose.onNodeWithText("Yadda saxla").assertIsEnabled()
    }
}
