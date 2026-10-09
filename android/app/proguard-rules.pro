# kotlinx.serialization 自带 consumer rules；这里只保留数据模型的序列化器以防万一。
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.wikg.aidaily.data.model.**$$serializer { *; }
-keepclassmembers class com.wikg.aidaily.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.wikg.aidaily.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# —— v1.1.1：R8 全量模式（AGP 9 默认）下的稳妥规则 ——
# kotlinx.serialization 官方推荐规则（@Serializable 类的 Companion / serializer() / $$serializer）
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.wikg.aidaily.data.model.** { *; }
# 入口与 WorkManager 反射实例化的 Worker
-keep class com.wikg.aidaily.AiDailyApp { <init>(); }
-keep class com.wikg.aidaily.crash.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
