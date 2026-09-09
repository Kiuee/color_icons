# 闪退修复：R8 的优化/混淆会破坏 ARSCLib 等运行时反射与结构敏感代码
# 保留收缩（未使用代码/资源仍被裁剪，体积收益保留），但关闭优化与改名
-dontoptimize
-dontobfuscate

# ARSCLib（APK 资源解析）结构敏感，整体保留避免 R8 误裁导致注入失败或启动崩溃
-keep class com.reandroid.** { *; }
-dontwarn com.reandroid.**
-dontwarn javax.xml.stream.**

