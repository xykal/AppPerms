# ---- Shizuku ----
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# ---- HiddenApiBypass (pakai refleksi/JNI internal) ----
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# ---- Inti OverlayOps: banyak refleksi ke framework + dipanggil dari berbagai tempat ----
-keep class app.overlayops.core.** { *; }
-keep class app.overlayops.model.** { *; }

# ViewBinding dipanggil langsung, tapi jaga-jaga untuk inflate lewat nama
-keep class app.overlayops.databinding.** { *; }

# Informasi yang dibutuhkan refleksi & stack trace yang kebaca
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Kotlin metadata (dipakai beberapa library)
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Enum dipakai lewat entries/values()
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
