package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.GeoPoint

/**
 * Rota başlangıcı (MAP.md 7.6, `LocationService.routeOrigin`): [last] ve Android'in bilinen son konumu [known] arasından en yenisi;
 * doğruluk 0–150 m, geçerli ve (0,0) değil, yaş −5 sn…3 dk. Hiçbiri uygun değilse null. İskelet (A6.2b).
 */
object RouteOrigin {
    fun routeOrigin(last: NavFix?, known: NavFix?, nowMs: Long): GeoPoint? = TODO("A6.6")
}
