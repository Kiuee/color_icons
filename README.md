# vivoicons（vivo 图标补丁）

一款 Android 应用，用于向 vivo 场景图标主题包 **SimpleIconThemeRes.apk** 中注入第三方应用的场景图标资源，让桌面图标在 vivo 主题机制下显示为自定义样式。

> ⚠️ 输出的 APK 为**未签名**状态，需要使用你自己的签名工具签名后安装。

## 功能特性

- **三步向导**：选择目标 APK → 添加资源组 → 一键注入，顶部波浪形步骤进度条可点击回退
- **批量队列**：可为多个包名连续添加资源组（mc 必选 / sc 可选），一次性统一注入
- **已扫描包名**：自动识别 APK 中已存在的场景图标包名（只读展示，支持搜索定位），防止重复导入造成资源 id 冲突
- **批量导入**：选择一个文件夹，按命名规则批量解析并加入队列，重复包名自动覆盖
- **路径记忆**：已选文件与队列持久化保存，应用意外退出后重开自动恢复；注入完成或主动重置后清除
- **自动覆盖**：输出目录存在同名 APK 时直接覆盖，并在成功页明确提示
- **叠加预览与缩略图**：队列项直接渲染 mc/sc 叠加效果，渲染失败时给出具体原因
- **自动清理**：打包完成后自动清除过程文件，应用目录副本仅保留最新一份
- **双击退出**：首页连按两次返回彻底退出，退出即清除全部记忆

## 使用流程

1. **获取目标 APK**：从 `/system/app/SimpleIconThemeRes/` 提取 `SimpleIconThemeRes.apk`（要求包名为 `com.vivo.simpleiconthemeres`）
2. **选择 APK**：应用自动解析包名、版本，并扫描可用的背景模板与已有包名
3. **添加资源**：输入目标应用包名（如 `com.coolapk.market`），分别选择 mc / sc 资源文件（vector XML），确认后进入队列；可继续添加多个包
4. **注入**：确认队列后开始注入，完成后输出 APK 到 `Download/vivoicons_patched/`，用签名工具签名后安装

### 批量导入命名规范

在电脑上把资源文件按以下格式命名后放入同一文件夹，在第二步使用「批量导入」：

```
<包名_下划线>_b_s5_1x1_mc.xml     例如 com_coolapk_market_b_s5_1x1_mc.xml
<包名_下划线>_b_s5_1x1_sc.xml     例如 com_coolapk_market_b_s5_1x1_sc.xml（可选）
```

## 技术原理

应用基于 [ARSCLib](https://github.com/REAndroid/ARSCLib) 直接在设备上修改 APK：

1. 解析 `resources.arsc`，定位 drawable 类型与背景模板资源
2. 将 mc/sc/bg 的 vector XML 写入 `res/drawable/`，并在资源表中注册同名 drawable 条目
3. 写出前将紧凑编码（OFFSET16/SPARSE）的类型块归一化为标准格式，保证 Android 严格解析器可接受
4. 输出未签名 APK 并自动自检（重新加载验证每个资源可解析）

## 从源码构建

| 要求 | 版本 |
|---|---|
| JDK | 17+ |
| Android Gradle Plugin | 9.2.1 |
| Gradle | 9.4.1（wrapper 自动下载） |
| compileSdk | 37 |
| 目标设备 | Android 7.0+（minSdk 24） |

```bash
# Debug 包
./gradlew :app:assembleDebug

# 运行单元测试
./gradlew :app:testDebugUnitTest
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## CI 自动发布

仓库内置 GitHub Actions（`.github/workflows/release.yml`）：

- 推送 `v*` 标签（如 `v1.0.0`）→ 自动构建 release APK 并发布到 GitHub Release
- 手动触发 → 仅构建并上传构建产物，不发布

正式签名需要在仓库 Secrets 中配置 `CI_KEYSTORE_BASE64` / `CI_KEYSTORE_PASSWORD` / `CI_KEY_ALIAS` / `CI_KEY_PASSWORD`（keystore 文件的 base64 及密码）；未配置时自动回退 debug 签名。

## 项目结构

```
app/src/main/java/com/example/vivoicons/
├── MainActivity.kt          # 入口
├── engine/                  # 注入引擎（纯逻辑，含单元测试）
│   ├── ApkPatcher.kt        #   APK 解析 / 资源注入 / 归一化 / 自检
│   └── CanvasAdapter.kt     #   画布适配工具
├── data/                    # DataStore 持久化（队列与记忆）
└── ui/                      # 界面（Material 3 + 动态取色）
    ├── home/                #   首页
    ├── steps/               #   三步向导
    ├── queue/               #   队列管理（已添加 / 已扫描）
    ├── success/             #   注入成功页
    ├── components/          #   波浪进度条 / 左滑操作 / 缩略图等
    └── theme/               #   主题（动态取色 + 完整兜底配色）
```

## 常见问题

**Q: 输出的 APK 安装提示「解析软件包时出现问题」？**
请确认已签名。若已签名仍失败，检查签名使用的 keystore 是否与已安装版本一致。

**Q: 提示包名不符？**
本工具仅支持 `com.vivo.simpleiconthemeres`，请从对应系统目录提取 APK。

**Q: 注入的图标在桌面不生效？**
确认注入的包名与目标应用一致（区分 mc/sc 两层），且 APK 已重签并覆盖安装了原主题包。

## 免责声明

本项目仅供学习研究与个人设备自定义主题使用，请勿用于分发他人应用的修改版本。使用本工具造成的任何问题由使用者自行承担。
