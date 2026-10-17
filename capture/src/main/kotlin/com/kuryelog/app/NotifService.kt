package com.kuryelog.app

import android.service.notification.NotificationListenerService

/**
 * Kurye uygulaması bildirimlerini okuma. Ad değişmez: bildirim erişimi `com.kuryelog.app.NotifService` adına bağlı
 * (plan Bölüm 2). Gövde Aşama 3'te (`notifs.jsonl`).
 */
class NotifService : NotificationListenerService()
