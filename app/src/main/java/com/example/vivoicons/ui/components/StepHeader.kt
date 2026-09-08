package com.example.vivoicons.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val STEP_TITLES = listOf("选择 APK", "添加资源", "注入")

/**
 * 顶部步骤进度（规范 C）：波浪形 active indicator + 三个可点击节点。
 * 只允许返回之前的步骤（[onStepClick] 由调用方保证）。
 */
@Composable
fun StepHeader(
    currentStep: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        WavyLinearProgressIndicator(
            progress = currentStep / 3f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            STEP_TITLES.forEachIndexed { index, title ->
                val stepNo = index + 1
                StepNode(
                    stepNo = stepNo,
                    title = title,
                    state = when {
                        stepNo < currentStep -> StepState.DONE
                        stepNo == currentStep -> StepState.CURRENT
                        else -> StepState.PENDING
                    },
                    enabled = stepNo < currentStep,
                    onClick = { onStepClick(stepNo) },
                )
            }
        }
    }
}

private enum class StepState { DONE, CURRENT, PENDING }

@Composable
private fun StepNode(
    stepNo: Int,
    title: String,
    state: StepState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = when (state) {
            StepState.DONE, StepState.CURRENT -> MaterialTheme.colorScheme.primary
            StepState.PENDING -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "nodeColor",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(26.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state == StepState.DONE) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = stepNo.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state == StepState.CURRENT) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (state == StepState.CURRENT) FontWeight.SemiBold else FontWeight.Normal,
            color = if (state == StepState.PENDING) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
