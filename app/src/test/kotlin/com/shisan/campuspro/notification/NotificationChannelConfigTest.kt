package com.shisan.campuspro.notification

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelConfigTest {
    @Test
    fun `all user visible channels support heads up notifications`() {
        val specs = CampusNotificationChannels.specs()

        assertTrue(specs.all { it.id.endsWith("_heads_up_v2") })
        assertTrue(specs.all { it.importance == NotificationManager.IMPORTANCE_HIGH })
        assertEquals(NotificationCompat.PRIORITY_HIGH, CampusNotificationChannels.notificationPriority)
    }

    @Test
    fun `notifications use a dedicated transient presentation`() {
        assertEquals(com.shisan.campuspro.R.drawable.ic_notification, CampusNotificationPresentation.smallIconRes)
        assertNotEquals(com.shisan.campuspro.R.drawable.ic_launcher, CampusNotificationPresentation.smallIconRes)
        assertTrue(CampusNotificationPresentation.autoCancel)
        assertFalse(CampusNotificationPresentation.ongoing)
        assertNull(CampusNotificationPresentation.timeoutAfterMillis)
    }
}
