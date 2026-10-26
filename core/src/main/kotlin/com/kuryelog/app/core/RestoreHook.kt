package com.kuryelog.app.core

import android.content.Context

/**
 * Yedekten geri yükleme bittikten sonra (S22 şifreli yedek; ana iş parçacığında, korumalı): `:orders` istatistiği yeniden kurar; `:app` bağlar.
 * S42: eski Rawstats ZIP'ini içe aktarma (`LogImport.start`, Kayıtlar düğmesi) kalktı; gün dosyaları yine `LogImporter` kurallarıyla birleşir.
 */
object RestoreHook {
    @Volatile var afterRestore: ((Context) -> Unit)? = null
}
