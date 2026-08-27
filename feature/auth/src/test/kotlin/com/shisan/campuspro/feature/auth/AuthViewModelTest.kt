package com.shisan.campuspro.feature.auth

import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.model.AuthState
import com.shisan.campuspro.core.model.LoginMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login uses selected login mode`() = runTest(dispatcher) {
        val repository = RecordingAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.selectLoginMode(LoginMode.JWXT_DIRECT)
        viewModel.setPrivacyTermsAccepted(true)
        viewModel.login("20260001", "secret", rememberCredentials = true)
        advanceUntilIdle()

        assertEquals(LoginMode.JWXT_DIRECT, repository.lastMode)
        assertTrue(repository.lastRememberCredentials)
    }

    @Test
    fun `default login mode is portal`() {
        val viewModel = AuthViewModel(RecordingAuthRepository())

        assertEquals(LoginMode.PORTAL, viewModel.uiState.value.mode)
        assertFalse(viewModel.uiState.value.rememberCredentials)
        assertFalse(viewModel.uiState.value.privacyTermsAccepted)
    }

    @Test
    fun `login without accepting privacy terms never calls repository`() = runTest(dispatcher) {
        val repository = RecordingAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.login("20260001", "secret", rememberCredentials = false)
        advanceUntilIdle()

        assertNull(repository.lastMode)
        assertEquals("请先阅读并同意隐私政策和用户协议", viewModel.uiState.value.error)
    }

    @Test
    fun `privacy terms selection can be changed before login`() {
        val viewModel = AuthViewModel(RecordingAuthRepository())

        viewModel.setPrivacyTermsAccepted(true)
        assertTrue(viewModel.uiState.value.privacyTermsAccepted)

        viewModel.setPrivacyTermsAccepted(false)
        assertFalse(viewModel.uiState.value.privacyTermsAccepted)
    }
}

private class RecordingAuthRepository : AuthRepository {
    override val authState: Flow<AuthState> = MutableStateFlow(AuthState(false, "", ""))
    var lastMode: LoginMode? = null
    var lastRememberCredentials = false

    override suspend fun login(
        username: String,
        password: String,
        mode: LoginMode,
        rememberCredentials: Boolean,
    ): Result<Unit> {
        lastMode = mode
        lastRememberCredentials = rememberCredentials
        return Result.success(Unit)
    }

    override suspend fun autoLogin(): Boolean = false

    override suspend fun logout() = Unit
}
