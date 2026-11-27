# Preserve game hooks and JVM metadata; rename implementation classes/members.
# No shrinking/optimization: Fabric, Mixin and JNI callbacks are runtime roots.
-dontshrink
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,NestHost,NestMembers,Record,BootstrapMethods

-keep class com.batclient.BatClientMod { *; }
-keep interface com.batclient.platform.PlatformEnvironment { *; }
-keep class com.batclient.platform.Platforms { *; }
# ServiceLoader provider names are part of the loader ABI.
-keep class com.batclient.platform.fabric.FabricEnvironment { *; }
-keep interface com.batclient.platform.ClientHooks { *; }
-keep interface com.batclient.platform.WorldFrame { *; }
-keep interface com.batclient.platform.BlockTarget { *; }
-keep interface com.batclient.platform.ClientHooks$* { *; }
-keep class com.batclient.platform.ClientHookServices { *; }
-keep class com.batclient.platform.fabric.FabricClientHooks { *; }
-keep class com.batclient.platform.fabric.BatFabricEntrypoint { *; }
-keep class com.batclient.mixin.** { *; }
-keep class com.batclient.performance.PerformanceMixinPlugin { *; }
-keep interface com.batclient.ServerListExtras { *; }
-keep interface com.batclient.ClientAccountAccess { *; }
-keep interface com.batclient.ChatCopyAccess { *; }

# Serialized field names and enum values are part of saved user configuration.
-keep class com.batclient.hud.layout.** { *; }
-keepclassmembers enum * { *; }
-keepclassmembers class * extends java.lang.Record { <fields>; }
# Gson discovers record accessors by persisted field name, including protected builds.
-keepclassmembers class com.batclient.DeathCoordinates$Death { *; }

# The native Ultralight bridge calls these Java interfaces by their method names.
-keepclassmembers class * implements io.github.nilsen84.ultralight.UltralightFilesystem { public *; }
-keepclassmembers class * implements io.github.nilsen84.ultralight.UltralightLogger { public *; }
-keepclassmembers class * implements io.github.nilsen84.ultralight.UltralightClipboard { public *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# Optional compile-time annotations are not used by runtime code.
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

-keep interface com.batclient.platform.ClientResources { *; }
-keep interface com.batclient.platform.ClientResources$* { *; }
-keep class com.batclient.platform.ClientResourceServices { *; }
-keep class com.batclient.platform.fabric.FabricClientResources { *; }
