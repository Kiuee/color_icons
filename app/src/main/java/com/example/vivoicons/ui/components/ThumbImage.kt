package com.example.vivoicons.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.example.vivoicons.data.PendingEntry
import com.example.vivoicons.ui.PatchViewModel

/**
 * 队列项缩略图（规范 B）：读取导入的 mc XML 渲染为图，sc 存在时叠加显示。
 * 渲染失败显示占位图标。
 */
@Composable
fun EntryThumbnail(vm: PatchViewModel, entry: PendingEntry, modifier: Modifier = Modifier) {
    val thumb by produceState<ImageBitmap?>(initialValue = null, entry) {
        value = vm.loadThumb(entry)
    }
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb!!,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
