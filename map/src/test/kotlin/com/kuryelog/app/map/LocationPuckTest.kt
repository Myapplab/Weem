package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** YolGo `puck_arrow_test.dart` 3. tanım (1; üç durumla; saf 2 test `PuckStyleTest`; A6.2b, MAP.md 6.4, 6.14): `LocationPuck` arrow/course → `puck-arrow` / `puck-dot` anahtarları. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocationPuckTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun okYalnizRotaVeYonVarkenYoksaNokta() {
        var arrow by mutableStateOf(true)
        var course by mutableStateOf<Double?>(45.0)
        compose.setContent { LocationPuck(course, arrow) }
        for ((a, c, key) in listOf(Triple(true, 45.0, "puck-arrow"), Triple(false, 45.0, "puck-dot"), Triple(true, null, "puck-dot"))) {
            arrow = a; course = c
            compose.waitForIdle()
            compose.onNodeWithTag(key).assertExists()
        }
    }
}
