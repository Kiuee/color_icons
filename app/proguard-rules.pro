# ARSCLib（APK 资源解析）使用反射较少但结构敏感，保守保留避免 R8 误裁导致注入失败
-keep class com.reandroid.arsc.chunk.** { *; }
-keep class com.reandroid.arsc.value.** { *; }
-keep class com.reandroid.arsc.pool.** { *; }
-keep class com.reandroid.xml.** { *; }
-dontwarn javax.xml.stream.**
