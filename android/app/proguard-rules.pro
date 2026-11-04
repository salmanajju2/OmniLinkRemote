# Keep WebRTC Native & Java Classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep Models for Gson Serialization
-keep class com.omnilink.remote.model.** { *; }

# Keep Accessibility and Services
-keep class com.omnilink.remote.service.** { *; }

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**
