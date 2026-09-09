package com.example.vivoicons.ui

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.vivoicons.ui.components.StepHeader
import com.example.vivoicons.ui.home.HomeScreen
import com.example.vivoicons.ui.queue.QueueManagerScreen
import com.example.vivoicons.ui.settings.SettingsScreen
import com.example.vivoicons.ui.settings.SettingsViewModel
import com.example.vivoicons.ui.settings.UpdateState
import com.example.vivoicons.ui.steps.Step1Apk
import com.example.vivoicons.ui.steps.Step2Resources
import com.example.vivoicons.ui.steps.Step3Inject
import com.example.vivoicons.ui.success.SuccessScreen
import com.example.vivoicons.ui.theme.AppShapes

/** 页面路由（Navigation Compose） */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val WIZARD = "wizard"
    const val QUEUE = "queue"
    const val SUCCESS = "success"
}

private val WIZARD_TITLES = mapOf(1 to "选择 APK", 2 to "添加资源", 3 to "注入")

/** 应用根：官方 Navigation Compose 路由（home/settings/wizard/queue/success） */
@Composable
fun AppRoot(vm: PatchViewModel, settingsVm: SettingsViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: Routes.HOME
    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }
    val success by vm.success.collectAsState()
    val update by settingsVm.update.collectAsState()
    var updateDialogDismissed by remember { mutableStateOf(false) }

    // 注入完成 → 跳转成功页（清空工作流栈，成功页返回即回首页）
    LaunchedEffect(success) {
        if (success != null && route != Routes.SUCCESS) {
            navController.navigate(Routes.SUCCESS) { popUpTo(Routes.HOME) }
        }
    }

    // 自动检查发现新版本 → 全局圆角提示对话框
    val showUpdateDialog = update is UpdateState.Available &&
        !updateDialogDismissed && route != Routes.SUCCESS
    if (showUpdateDialog) {
        val available = update as UpdateState.Available
        AlertDialog(
            onDismissRequest = { updateDialogDismissed = true },
            shape = AppShapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("发现新版本 ${available.version}") },
            text = { Text("前往 GitHub Release 页面下载最新 APK。") },
            confirmButton = {
                TextButton(onClick = {
                    updateDialogDismissed = true
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, available.url.toUri()),
                    )
                }) { Text("前往下载") }
            },
            dismissButton = {
                TextButton(onClick = { updateDialogDismissed = true }) { Text("忽略") }
            },
        )
    }

    // 首页双击返回退出（规范 A）
    BackHandler(enabled = route == Routes.HOME) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAt < 2000) {
            vm.clearMemoryAndExit()
            (context as? Activity)?.finishAffinity()
        } else {
            lastBackAt = now
            Toast.makeText(context, "再按一次返回键退出并清除记忆", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 底部导航栏仅在首页 / 设置页显示
            if (route == Routes.HOME || route == Routes.SETTINGS) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    NavigationBarItem(
                        selected = route == Routes.HOME,
                        onClick = {
                            if (route != Routes.HOME) {
                                navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                            }
                        },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        label = { Text("首页") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.SETTINGS,
                        onClick = {
                            if (route != Routes.SETTINGS) {
                                navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                            }
                        },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding),
            enterTransition = {
                val f = tween<Float>(220)
                fadeIn(f)
            },
            exitTransition = {
                val f = tween<Float>(180)
                fadeOut(f)
            },
            popEnterTransition = {
                val f = tween<Float>(220)
                fadeIn(f)
            },
            popExitTransition = {
                val f = tween<Float>(180)
                fadeOut(f)
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(vm = vm, onOpenWizard = { navController.navigate(Routes.WIZARD) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(vm = settingsVm) }
            composable(Routes.WIZARD) {
                WizardScaffold(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                )
            }
            composable(Routes.QUEUE) {
                QueueManagerScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onEditItem = { index ->
                        vm.editFromQueue(index)
                        navController.navigate(Routes.WIZARD)
                    },
                    onEditSelected = {
                        if (vm.editSelectedSequentially()) navController.navigate(Routes.WIZARD)
                    },
                )
            }
            composable(Routes.SUCCESS) {
                SuccessScreen(vm = vm, onDone = { navController.popBackStack() })
            }
        }
    }
}

/** 向导壳（规范 B/C/D/H）：AppBar（返回/重置/队列徽章）+ 波浪步骤条 + 右下角 Extended FAB */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScaffold(
    vm: PatchViewModel,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val state by vm.ui.collectAsState()
    val step = state.step
    var showResetConfirm by remember { mutableStateOf(false) }

    // 离开向导时清理临时编辑状态
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.resetTransient() }
    }

    // 加入队列时的徽章脉冲动画（规范 F）
    val badgeScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(state.badgePulse) {
        if (state.badgePulse > 0) {
            badgeScale.snapTo(1.4f)
            badgeScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(WIZARD_TITLES[step] ?: "", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 重置（保留）
                    IconToggleButton(
                        checked = false,
                        onCheckedChange = { showResetConfirm = true },
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "重置全部")
                    }
                    // 队列入口：徽章显示包数
                    IconToggleButton(
                        checked = false,
                        onCheckedChange = { onOpenQueue() },
                    ) {
                        BadgedBox(
                            badge = {
                                if (state.queueBadge > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .scale(badgeScale.value),
                                    ) {
                                        Text("${state.queueBadge}")
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = "队列管理")
                        }
                    }
                },
            )
        },
        floatingActionButton = { WizardFab(vm, step, onBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StepHeader(
                currentStep = step,
                onStepClick = { target -> vm.backToStep(target) },
            )
            androidx.compose.animation.AnimatedContent(
                targetState = step,
                label = "stepContent",
                transitionSpec = {
                    // 步骤间只做轻柔淡入淡出，方向感交给外层导航转场
                    fadeIn(tween(240)) togetherWith fadeOut(tween(160))
                },
            ) { current ->
                Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    when (current) {
                        1 -> Step1Apk(vm)
                        2 -> Step2Resources(vm)
                        else -> Step3Inject(vm)
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            shape = AppShapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("重置全部？") },
            text = { Text("将清除所选 APK、队列与全部记忆，回到首页。") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    vm.clearMemory()
                    onBack()
                }) { Text("重置", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 右下角 Extended FAB（规范 D/E/F/G）：本步未完成时隐藏不占位 */
@Composable
private fun WizardFab(vm: PatchViewModel, step: Int, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    val editing = state.editingIndex >= 0

    Column(horizontalAlignment = Alignment.End) {
        // Step2：加入队列成功后，FAB 上方出现「继续添加」（规范 F）
        androidx.compose.animation.AnimatedVisibility(
            visible = step == 2 && !editing && state.entryJustAdded,
            enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
        ) {
            FilledTonalButton(
                onClick = { vm.resetEntryInputs() },
                shape = AppShapes.large,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("继续添加")
            }
        }
        Spacer(Modifier.height(10.dp))

        when (step) {
            1 -> {
                FabAnimated(visible = state.canGoStep2) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.nextStep() },
                        shape = AppShapes.large,
                    ) {
                        Text("下一步")
                        Spacer(Modifier.size(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
            2 -> {
                // 编辑中必须填完才能保存；新增模式：队列非空即随时可下一步（规范 B）
                val fabVisible = if (editing) state.canConfirmEntry
                else state.canConfirmEntry || state.entryJustAdded || state.queue.isNotEmpty()
                FabAnimated(visible = fabVisible) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            when {
                                editing -> {
                                    if (vm.confirmEntry() && !vm.hasMoreToEdit) {
                                        vm.finishEdit()
                                        onBack()
                                    }
                                }
                                state.entryJustAdded -> vm.nextStep()
                                state.canConfirmEntry -> vm.confirmEntry()
                                else -> vm.nextStep()
                            }
                        },
                        shape = AppShapes.large,
                    ) {
                        Text(
                            when {
                                editing -> "保存修改"
                                state.entryJustAdded -> "下一步"
                                state.canConfirmEntry -> "加入队列"
                                else -> "下一步"
                            },
                        )
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            when {
                                state.canConfirmEntry && !editing && !state.entryJustAdded ->
                                    Icons.Default.Add
                                else -> Icons.AutoMirrored.Filled.ArrowForward
                            },
                            contentDescription = null,
                        )
                    }
                }
            }
            else -> {
                FabAnimated(
                    visible = state.queue.isNotEmpty() && !state.injecting && state.canGoStep2,
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.startInject() },
                        shape = AppShapes.large,
                    ) {
                        Text("开始注入")
                        Spacer(Modifier.size(6.dp))
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun FabAnimated(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.scaleIn(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        ) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
    ) { content() }
}
