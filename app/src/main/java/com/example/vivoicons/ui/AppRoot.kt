package com.example.vivoicons.ui

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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

private val WIZARD_TITLES = mapOf(1 to "选择 APK", 2 to "添加资源", 3 to "注入")

/**
 * 应用根：覆盖式导航。
 * 底层 = 首页/设置（底栏常驻，永不消失）；流程页（向导/队列/成功）作为全屏覆盖层
 * 从右侧滑入盖住底栏——转场全程底栏不动、不闪失。
 */
@Composable
fun AppRoot(vm: PatchViewModel, settingsVm: SettingsViewModel) {
    val state by vm.ui.collectAsState()
    val success by vm.success.collectAsState()
    val update by settingsVm.update.collectAsState()
    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }
    var updateDialogDismissed by remember { mutableStateOf(false) }

    // 底栏选中态（官方 Navigation.kt 写法：rememberSaveable + ordinal）
    var selectedDestination by rememberSaveable { mutableIntStateOf(0) }

    /** 底栏 Tab 切换（首页 ⇄ 设置交叉淡入淡出） */
    fun navigateTab(index: Int) {
        selectedDestination = index
    }

    // 注入完成 → 以成功页覆盖层替换整个流程
    LaunchedEffect(success) {
        if (success != null) vm.openSuccess()
    }

    // 返回：覆盖层逐层弹出；无覆盖层时首页双击退出
    BackHandler(enabled = state.detailStack.isNotEmpty()) { vm.closeDetail() }
    BackHandler(enabled = state.detailStack.isEmpty()) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAt < 2000) {
            vm.clearMemoryAndExit()
            (context as? Activity)?.finishAffinity()
        } else {
            lastBackAt = now
            Toast.makeText(context, "再按一次返回键退出并清除记忆", Toast.LENGTH_SHORT).show()
        }
    }

    // 自动检查发现新版本 → 圆角提示对话框
    val showUpdateDialog = update is UpdateState.Available && !updateDialogDismissed
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
                    context.startActivity(Intent(Intent.ACTION_VIEW, available.url.toUri()))
                }) { Text("前往下载") }
            },
            dismissButton = {
                TextButton(onClick = { updateDialogDismissed = true }) { Text("忽略") }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 基座：首页/设置 + 常驻底栏（官方 Navigation.kt 结构：底栏无条件组合）
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    windowInsets = NavigationBarDefaults.windowInsets,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    NavigationBarItem(
                        selected = selectedDestination == 0,
                        onClick = { navigateTab(0) },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        label = { Text("首页") },
                    )
                    NavigationBarItem(
                        selected = selectedDestination == 1,
                        onClick = { navigateTab(1) },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = selectedDestination,
                label = "topTabs",
                transitionSpec = {
                    // 官方 Tab 样式：轻柔交叉淡入淡出
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
            ) { selected ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if (selected == 0) {
                        HomeScreen(vm = vm, onOpenWizard = { vm.openDetail(DetailRoutes.WIZARD) })
                    } else {
                        SettingsScreen(vm = settingsVm)
                    }
                }
            }
        }

        // 覆盖层：全屏流程页，滑入盖住底栏（推入时下层原地保留，绝不露出首页）
        AnimatedContent(
            targetState = state.detailStack,
            label = "detailOverlay",
            transitionSpec = {
                val push = targetState.size > initialState.size
                if (push) {
                    // 新页滑入盖住；下层原地保留（KeepUntilTransitionsFinished）
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        tween(300, easing = FastOutSlowInEasing),
                    ) togetherWith ExitTransition.KeepUntilTransitionsFinished
                } else {
                    // 栈顶向右滑走；同时露出下层从左侧滑回（避免空帧闪现基座）
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        tween(300, easing = FastOutSlowInEasing),
                    ) togetherWith slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        tween(300, easing = FastOutSlowInEasing),
                    )
                }
            },
        ) { stack ->
            stack.lastOrNull()?.let { detail ->
                Box(Modifier.fillMaxSize()) {
                    when (detail) {
                        DetailRoutes.WIZARD -> WizardScaffold(
                            vm = vm,
                            onBack = { vm.closeDetail() },
                            onOpenQueue = { vm.openDetail(DetailRoutes.QUEUE) },
                        )
                        DetailRoutes.QUEUE -> QueueManagerScreen(
                            vm = vm,
                            onBack = { vm.closeDetail() },
                            onEditItem = { index ->
                                vm.editFromQueue(index)
                                vm.openDetail(DetailRoutes.WIZARD)
                            },
                            onEditSelected = {
                                if (vm.editSelectedSequentially()) vm.openDetail(DetailRoutes.WIZARD)
                            },
                        )
                        DetailRoutes.SUCCESS -> SuccessScreen(vm = vm, onDone = {
                            vm.finishSuccess()
                            vm.closeAllDetails()
                        })
                        else -> Box(Modifier.fillMaxSize())
                    }
                }
            } ?: Box(Modifier.fillMaxSize())
        }
    }
}

/** 向导覆盖层（规范 B/C/D/H）：AppBar（返回/重置/队列徽章）+ 波浪步骤条 + 右下角 Extended FAB */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScaffold(
    vm: PatchViewModel,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val state by vm.ui.collectAsState()
    val step = state.step
    val editing = state.editingIndex >= 0 || state.multiEditQueue.isNotEmpty()
    var showResetConfirm by remember { mutableStateOf(false) }

    // 向导内返回：编辑态回队列；步骤 >1 回退一步；第 1 步清理临时状态后真正退出
    fun wizardBack() {
        when {
            editing -> {
                if (vm.confirmEntry() && !vm.hasMoreToEdit) {
                    vm.finishEdit()
                    onBack()
                }
            }
            step > 1 -> vm.goStep(step - 1)
            else -> {
                vm.resetTransient()
                onBack()
            }
        }
    }

    // 接管系统返回：向导内逐级回退，而不是直接退出整个向导
    BackHandler { wizardBack() }

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
                    IconButton(onClick = { wizardBack() }) {
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
            AnimatedContent(
                targetState = step,
                label = "stepContent",
                transitionSpec = {
                    // 向导内步骤切换：前进右滑入，后退反向滑回
                    val forward = targetState > initialState
                    if (forward) {
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } +
                            fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                            slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } +
                            fadeOut(tween(300, easing = FastOutSlowInEasing))
                    } else {
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } +
                            fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                            slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } +
                            fadeOut(tween(300, easing = FastOutSlowInEasing))
                    }
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
