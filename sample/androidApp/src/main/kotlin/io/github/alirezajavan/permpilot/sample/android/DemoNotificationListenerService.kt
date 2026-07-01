package io.github.alirezajavan.permpilot.sample.android

import android.service.notification.NotificationListenerService

/**
 * Deliberately empty: the notification-listener access Settings screen only lists apps that
 * declare a [NotificationListenerService], so the demo needs one just to be grantable there.
 * PermPilot's `Permission.NotificationListenerAccess` reports
 * `ConfigurationError(MissingManifestDeclaration)` for apps that don't declare one.
 */
class DemoNotificationListenerService : NotificationListenerService()
