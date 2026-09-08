package com.example.vivoicons.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 左滑露出右侧操作区（规范 B）：material3 1.4.0 无 SwipeToActionsBox，自绘实现。
 * 向左拖动露出 [endContent]（编辑/删除），松手就近吸附；revealed 状态变化回调 [onRevealedChange]。
 */
@Composable
fun SwipeRevealBox(
    modifier: Modifier = Modifier,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    endContent: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var revealWidth by remember { mutableFloatStateOf(0f) }

    // 外部 revealed 状态同步（多选切换、删除后复位）
    LaunchedEffect(revealed, revealWidth) {
        val target = if (revealed) -revealWidth else 0f
        if (abs(offsetX.value - target) > 1f) {
            offsetX.animateTo(target, spring(stiffness = 380f))
        }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .onSizeChanged { revealWidth = it.width.toFloat() },
            verticalAlignment = Alignment.CenterVertically,
            content = endContent,
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val target = (offsetX.value + delta).coerceIn(-revealWidth, 0f)
                        scope.launch { offsetX.snapTo(target) }
                    },
                    onDragStopped = {
                        val open = offsetX.value < -revealWidth / 2f
                        scope.launch {
                            offsetX.animateTo(
                                if (open) -revealWidth else 0f,
                                spring(stiffness = 380f),
                            )
                            onRevealedChange(open)
                        }
                    },
                ),
        ) {
            content()
        }
    }
}
