# 保持 SuperLyricApi 的回调入口，避免未来开启混淆时 AIDL/Binder 回调被裁剪。
-keep class com.hchen.superlyricapi.** { *; }
-keep class com.hchen.superlyricapi.model.** { *; }
