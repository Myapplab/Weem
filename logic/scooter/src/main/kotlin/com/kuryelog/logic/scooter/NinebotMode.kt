package com.kuryelog.logic.scooter

/** Fixed field-tested PRE_COMM input; old zero-mode preferences migrate without losing data (SCOOTER.md 4.3; NB `NinebotMode.java`). */
enum class NinebotMode(val setting: String, val firmwareInput: Boolean) {
    GEN2_FW("gen2_fw", true);

    companion object {
        fun parse(value: String?): NinebotMode {
            if ("gen3_zero" == value || GEN2_FW.setting == value) return GEN2_FW
            throw IllegalArgumentException("unknown crypto mode")
        }
    }
}
