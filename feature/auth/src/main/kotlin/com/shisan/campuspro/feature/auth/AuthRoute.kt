package com.shisan.campuspro.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.shisan.campuspro.core.data.AuthRepository
import com.shisan.campuspro.core.designsystem.CampusButton
import com.shisan.campuspro.core.designsystem.CampusErrorBanner
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.designsystem.CampusTextField
import com.shisan.campuspro.core.model.LoginMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AuthUiState(
    val mode: LoginMode = LoginMode.PORTAL,
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val rememberCredentials: Boolean = false,
    val privacyTermsAccepted: Boolean = false,
)

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun selectLoginMode(mode: LoginMode) {
        _uiState.update {
            if (it.loading) it else it.copy(mode = mode, error = null)
        }
    }

    fun setRememberCredentials(enabled: Boolean) {
        _uiState.update { if (it.loading) it else it.copy(rememberCredentials = enabled) }
    }

    fun setPrivacyTermsAccepted(accepted: Boolean) {
        _uiState.update { if (it.loading) it else it.copy(privacyTermsAccepted = accepted, error = null) }
    }

    fun login(username: String, password: String, rememberCredentials: Boolean) {
        if (!_uiState.value.privacyTermsAccepted) {
            _uiState.update {
                it.copy(
                    rememberCredentials = rememberCredentials,
                    error = "请先阅读并同意隐私政策和用户协议",
                    loggedIn = false,
                )
            }
            return
        }
        val mode = _uiState.value.mode
        _uiState.update { it.copy(rememberCredentials = rememberCredentials) }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, loggedIn = false) }
            val result = repository.login(
                username,
                password,
                mode,
                rememberCredentials,
            )
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(loading = false, loggedIn = true) },
                onFailure = { _uiState.value.copy(loading = false, error = it.message ?: "登录失败") },
            )
        }
    }
}

@Composable
fun AuthRoute(
    repository: AuthRepository,
    onLoggedIn: (LoginMode) -> Unit,
    onAcceptPrivacy: suspend () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenUserAgreement: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(uiState.loggedIn) {
        if (uiState.loggedIn) onLoggedIn(uiState.mode)
    }
    AuthScreen(
        uiState = uiState,
        onLogin = { username, password, rememberCredentials ->
            scope.launch {
                onAcceptPrivacy()
                viewModel.login(username, password, rememberCredentials)
            }
        },
        onRememberCredentialsChange = viewModel::setRememberCredentials,
        onPrivacyTermsAcceptedChange = viewModel::setPrivacyTermsAccepted,
        onLoginModeSelected = viewModel::selectLoginMode,
        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
        onOpenUserAgreement = onOpenUserAgreement,
        modifier = modifier,
    )
}

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onLogin: (String, String, Boolean) -> Unit,
    onRememberCredentialsChange: (Boolean) -> Unit,
    onPrivacyTermsAcceptedChange: (Boolean) -> Unit,
    onLoginModeSelected: (LoginMode) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        val compactHeight = maxHeight < 480.dp
        if (maxWidth >= 840.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 1080.dp).align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuthBrand(modifier = Modifier.weight(1f))
                AuthForm(
                    uiState = uiState,
                    onLogin = onLogin,
                    onRememberCredentialsChange = onRememberCredentialsChange,
                    onPrivacyTermsAcceptedChange = onPrivacyTermsAcceptedChange,
                    onLoginModeSelected = onLoginModeSelected,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onOpenUserAgreement = onOpenUserAgreement,
                    modifier = Modifier.widthIn(max = 420.dp).weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .align(if (maxWidth < 600.dp) Alignment.TopCenter else Alignment.Center)
                    .padding(top = if (maxWidth < 600.dp) if (compactHeight) 24.dp else 88.dp else 0.dp, bottom = 32.dp),
            ) {
                AuthBrand()
                Spacer(Modifier.height(24.dp))
                AuthForm(
                    uiState = uiState,
                    onLogin = onLogin,
                    onRememberCredentialsChange = onRememberCredentialsChange,
                    onPrivacyTermsAcceptedChange = onPrivacyTermsAcceptedChange,
                    onLoginModeSelected = onLoginModeSelected,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onOpenUserAgreement = onOpenUserAgreement,
                )
            }
        }
    }
}

@Composable
private fun AuthBrand(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        StaggeredEnterItem(delayMillis = 0) {
            Text("闪电课表", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        StaggeredEnterItem(delayMillis = 80) {
            Text(
                "连接教务系统，同步你的校园安排",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun AuthForm(
    uiState: AuthUiState,
    onLogin: (String, String, Boolean) -> Unit,
    onRememberCredentialsChange: (Boolean) -> Unit,
    onPrivacyTermsAcceptedChange: (Boolean) -> Unit,
    onLoginModeSelected: (LoginMode) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        StaggeredEnterItem(delayMillis = 160, modifier = Modifier.fillMaxWidth()) {
            LoginForm(
                mode = uiState.mode,
                loading = uiState.loading,
                rememberCredentials = uiState.rememberCredentials,
                privacyTermsAccepted = uiState.privacyTermsAccepted,
                onModeSelected = onLoginModeSelected,
                onRememberCredentialsChange = onRememberCredentialsChange,
                onPrivacyTermsAcceptedChange = onPrivacyTermsAcceptedChange,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                onOpenUserAgreement = onOpenUserAgreement,
                onLogin = onLogin,
            )
        }
        uiState.error?.let {
            Spacer(Modifier.height(12.dp))
            StaggeredEnterItem(delayMillis = 0, modifier = Modifier.fillMaxWidth()) {
                CampusErrorBanner(message = it)
            }
        }
    }
}

@Composable
private fun StaggeredEnterItem(
    delayMillis: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            initialOffsetY = { -24 },
        ),
    ) {
        content()
    }
}

@Composable
private fun LoginForm(
    mode: LoginMode,
    loading: Boolean,
    rememberCredentials: Boolean,
    privacyTermsAccepted: Boolean,
    onModeSelected: (LoginMode) -> Unit,
    onRememberCredentialsChange: (Boolean) -> Unit,
    onPrivacyTermsAcceptedChange: (Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onLogin: (String, String, Boolean) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // 点击登录时：未同意隐私条款则弹窗确认，已同意则直接登录
    val attemptLogin = {
        if (!privacyTermsAccepted) {
            showPrivacyDialog = true
        } else {
            onLogin(username, password, rememberCredentials)
        }
    }

    if (showPrivacyDialog) {
        PrivacyConfirmDialog(
            onConfirm = {
                showPrivacyDialog = false
                onPrivacyTermsAcceptedChange(true)
                onLogin(username, password, rememberCredentials)
            },
            onDismiss = { showPrivacyDialog = false },
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onOpenUserAgreement = onOpenUserAgreement,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoginModeSelector(
                selectedMode = mode,
                enabled = !loading,
                onSelected = onModeSelected,
            )
            CampusTextField(value = username, onValueChange = { username = it }, label = "学号", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            CampusTextField(
                value = password,
                onValueChange = { password = it },
                label = "密码",
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) CampusIcons.Visibility else CampusIcons.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rememberCredentials,
                    onCheckedChange = onRememberCredentialsChange,
                    enabled = !loading,
                )
                Text(
                    "记住密码",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            CampusButton(
                text = if (loading) "登录中..." else "登录",
                onClick = attemptLogin,
                enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            )
            PrivacyTermsConsent(
                accepted = privacyTermsAccepted,
                enabled = !loading,
                onAcceptedChange = onPrivacyTermsAcceptedChange,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                onOpenUserAgreement = onOpenUserAgreement,
            )
        }
    }
}

@Composable
private fun PrivacyConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenUserAgreement: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务条款") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "登录前，请阅读并同意以下条款：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "《隐私政策》",
                        modifier = Modifier.clickable(onClick = onOpenPrivacyPolicy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("和", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "《用户协议》",
                        modifier = Modifier.clickable(onClick = onOpenUserAgreement),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("同意并登录", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PrivacyTermsConsent(
    accepted: Boolean,
    enabled: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenUserAgreement: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = accepted,
            onCheckedChange = onAcceptedChange,
            enabled = enabled,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = "我已阅读并同意",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "《隐私政策》",
            modifier = Modifier.clickable(enabled = enabled, onClick = onOpenPrivacyPolicy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "和",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "《用户协议》",
            modifier = Modifier.clickable(enabled = enabled, onClick = onOpenUserAgreement),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LoginModeSelector(
    selectedMode: LoginMode,
    enabled: Boolean,
    onSelected: (LoginMode) -> Unit,
) {
    val options = LoginMode.entries.toList()
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                enabled = enabled,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
                label = { Text(mode.label) },
            )
        }
    }
}

private val LoginMode.label: String
    get() = when (this) {
        LoginMode.PORTAL -> "门户统一认证"
        LoginMode.JWXT_DIRECT -> "教务系统直登"
    }

private class AuthViewModelFactory(
    private val repository: AuthRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repository) as T
}
