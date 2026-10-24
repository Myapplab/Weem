package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.logic.map.graph.MapAssets
import com.kuryelog.logic.map.ui.BasemapInstaller
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Gömülü vektör altlık (S39; MAP.md 6.19): süreç açılınca APK'daki Bakü karoları ve glifler IO'da bir kez `filesDir/basemap`'e kopyalanır
 * ([BasemapInstaller]); MapLibre vektör altlığı oradan, ağsız çizer. Karosuz APK'da [State.Online] (OpenFreeMap).
 */
object Basemap {
    sealed interface State {
        /** Kopya sürüyor (ilk açılış/yeni APK; saniyeden kısa): vektör stil beklenir, ağdan inip sonra değişmesin. */
        data object Pending : State

        data class Ready(val local: BasemapInstaller.Local) : State

        /** APK'da karo yok ya da kopya başarısız: OpenFreeMap. */
        data object Online : State
    }

    private val _state = MutableStateFlow<State>(State.Pending)
    val state: StateFlow<State> = _state

    @Volatile private var installer: BasemapInstaller? = null
    private val styles = HashMap<String, String>()

    /** Süreç başında bir kez (`WeemApplication`; harita yüzeyi de çağırır); sonraki çağrılar bir şey yapmaz. */
    fun start(context: Context) {
        if (installer != null) return
        val app = context.applicationContext
        val inst = synchronized(this) {
            installer?.let { return }
            BasemapInstaller(File(app.filesDir, "basemap"), assets(app)).also { installer = it }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Ne olursa olsun bekleme biter (bellek, disk dolu …): kopya yoksa altlık ağdan.
            _state.value = runCatching { inst.install() }.getOrNull()?.let { State.Ready(it) } ?: State.Online
        }
    }

    /** Yerel stil JSON'u (tema başına bir kez okunur); şablon yoksa null. */
    fun style(ready: State.Ready, dark: Boolean): String? = synchronized(styles) {
        val key = "${ready.local.version}|$dark"
        styles[key] ?: installer?.style(ready.local, dark)?.also { styles[key] = it }
    }

    private fun assets(app: Context) = object : MapAssets {
        override fun read(name: String): ByteArray? = open(name)?.use { it.readBytes() }

        override fun open(name: String): InputStream? = try {
            app.assets.open("basemap/$name")
        } catch (_: FileNotFoundException) {
            null
        }
    }
}
