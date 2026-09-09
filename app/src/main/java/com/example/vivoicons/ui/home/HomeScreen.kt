package com.example.vivoicons.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vivoicons.ui.PatchViewModel
import com.example.vivoicons.ui.theme.AppShapes

/** 首页：hero 区 + 一张大圆角信息卡（条目间白色细分隔线，内容在 HomeInfo.kt 里改代码维护） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: PatchViewModel, onOpenWizard: () -> Unit) {
    val scroll = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 底部 inset 已由外层 Scaffold 的 NavigationBar 处理，避免重复叠加出现空白条
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("vivo 图标补丁", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // hero：应用标识
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "vivo 场景图标资源注入",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 主要位置的「开始导入」按钮
            Button(
                onClick = onOpenWizard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = AppShapes.large,
            ) {
                Text("开始导入", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }

            // 信息：单张大圆角卡片，条目间白色细分隔线
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column {
                    HOME_INFO_ITEMS.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                thickness = 1.dp,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            item.imageRes?.let { res ->
                                Spacer(Modifier.height(8.dp))
                                Image(
                                    painter = painterResource(res),
                                    contentDescription = item.title,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (item.body.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (item.imageRes == null) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "（预留）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
