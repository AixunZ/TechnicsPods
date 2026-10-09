-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keep class dev.technics.pods.capture.CaptureReceiver { *; }
-keep class dev.technics.pods.MainActivity { *; }
-keep class dev.technics.pods.TechnicsPodsApp { *; }
