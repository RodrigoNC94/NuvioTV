package com.nuvio.tv.data.local

enum class NightModeLevel(val key: String) {
    OFF("off"),
    LIGHT("light"),
    MEDIUM("medium"),
    STRONG("strong");

    companion object {
        fun fromKey(key: String?): NightModeLevel = entries.firstOrNull { it.key == key } ?: OFF
    }
}
