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
