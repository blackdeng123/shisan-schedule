package com.shisan.campuspro

import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.datastore.PrivacyConsentStore
import com.shisan.campuspro.notification.NotificationCoordinator

/**
 * 隐私合规会话管理器：聚合隐私同意状态、登出与通知清理。
 *
 * 从 DI 容器中提取，容器只需持有本类的实例并委托调用。
 */
class PrivacySessionManager(
    private val privacyConsentStore: PrivacyConsentStore,
    private val authRepository: AuthRepository,
    private val notificationCoordinator: NotificationCoordinator,
) {
    suspend fun prepareState() {
        privacyConsentStore.migrateLegacyCredentials {
            authRepository.logout()
        }
    }

    suspend fun accept() {
        privacyConsentStore.accept()
    }

    suspend fun revoke() {
        privacyConsentStore.revoke()
        notificationCoordinator.clearState()
        authRepository.logout()
    }
}
