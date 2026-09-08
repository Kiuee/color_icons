package com.example.vivoicons.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.vivoicons.ui.components.StepHeader
import com.example.vivoicons.ui.home.HomeScreen
import com.example.vivoicons.ui.queue.QueueManagerScreen
import com.example.vivoicons.ui.steps.Step1Apk
import com.example.vivoicons.ui.steps.Step2Resources
import com.example.vivoicons.ui.steps.Step3Inject
import com.example.vivoicons.ui.theme.AppShapes

/** 应用根：按返回栈渲染 首页 / 向导 / 队列管理 / 成功页（规范 H：统一返回逻辑） */
@Composable
fun AppRoot(vm: PatchViewModel) {
    val state by vm.ui.collectAsState()
    BackHandler(enabled = state.backStack.size > 1 && state.screen != Screen.Success) { vm.back() }

    // 首页双击返回退出：第一击提示，2 秒内第二击清除记忆并彻底退出（规范 A）
    val context = androidx.compose.ui.platform.LocalContext.current
    var lastBackAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    BackHandler(enabled = state.backStack.size == 1) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt < 2000) {
            vm.clearMemoryAndExit()
            (context as? android.app.Activity)?.finishAffinity()
        } else {
            lastBackAt = now
            android.widget.Toast
                .makeText(context, "再按一次返回键退出并清除记忆", android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    // 按栈深度判定方向：前进=新页从右滑入；返回=从左滑回
    val navKey = state.backStack.size to state.screen
    AnimatedContent(
        targetState = navKey,
        label = "appRoot",
        transitionSpec = {
            val forward = targetState.first >= initialState.first
            val slideSpec = tween<IntOffset>(220)
            val fadeSpec = tween<Float>(220)
            if (forward) {
                (slideInHorizontally(slideSpec) { it / 4 } + fadeIn(fadeSpec)) togetherWith
                    (slideOutHorizontally(slideSpec) { -it / 4 } + fadeOut(fadeSpec))
            } else {
                (slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(fadeSpec)) togetherWith
                    (slideOutHorizontally(slideSpec) { it / 4 } + fadeOut(fadeSpec))
            }
        },
    ) { (_, screen) ->
        when (screen) {
            is Screen.Wizard -> WizardScaffold(vm, screen.step)
            is Screen.QueueManager -> QueueManagerScreen(vm)
            Screen.Success -> com.example.vivoicons.ui.success.SuccessScreen(vm)
            Screen.Home -> HomeScreen(vm)
        }
    }
}

private val WIZARD_TITLES = mapOf(1 to "选择 APK", 2 to "添加资源", 3 to "注入")

/** 向导壳（规范 B/C/D/H）：AppBar（返回/重置/队列徽章）+ 波浪步骤条 + 右下角 Extended FAB */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScaffold(vm: PatchViewModel, step: Int) {
    val state by vm.ui.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }

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
                    Text(
                        WIZARD_TITLES[step] ?: "",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
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
                    // 队列入口：徽章显示包数，替代原「队列中 X 个包」Chip
                    IconToggleButton(
                        checked = false,
                        onCheckedChange = { vm.openQueueManager() },
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
        floatingActionButton = { WizardFab(vm, step) },
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
                    // 步骤间只做轻柔淡入淡出，方向感交给外层导航转场，避免双重滑动
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
private fun WizardFab(vm: PatchViewModel, step: Int) {
    val state by vm.ui.collectAsState()
    val editing = state.editingIndex >= 0

    Column(horizontalAlignment = Alignment.End) {
        // Step2：加入队列成功后，FAB 上方出现「继续添加」（规范 F）
        AnimatedVisibility(
            visible = step == 2 && !editing && state.entryJustAdded,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
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
                                    if (vm.confirmEntry() && !vm.hasMoreToEdit) vm.finishEdit()
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
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) { content() }
}
