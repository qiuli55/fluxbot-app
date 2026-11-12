# WebView JS 桥：方法通过 @JavascriptInterface 由网页按名字调用，
# 不能被 R8 重命名或删除；注解属性也必须保留，否则注入会失效。
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 崩溃报告需要原始行号；同时统一 source file 名，避免暴露构建机路径。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 本应用的数据持久化全部走 Models.kt / Store.kt / SecureStore.kt 里手写的
# org.json JSONObject 字段映射（键是字符串字面量），没有 Gson、kotlinx.serialization、
# Class.forName 反射，也没有按 class/enum 名反序列化的逻辑。
# FIcon 等 enum 只用于 UI 分发，其 .name 不落盘、不参与 valueOf 读回。
# 因此无需额外 keep 数据类或枚举；R8 默认保留 Java/Kotlin 枚举名，后续若新增
# 按 name/valueOf 序列化的枚举，再针对性补 keep 规则。
