# GS Intelligence SDK — consumer ProGuard/R8 rules.
# Keep the public API surface and token data classes intact under obfuscation.
-keep public class com.gammasweep.gsintelligence.GS { public *; }
-keep public class com.gammasweep.gsintelligence.GSConfig { *; }
-keep public class com.gammasweep.gsintelligence.GpsMode { *; }
-keep public class com.gammasweep.gsintelligence.GSException { *; }
-keep public class com.gammasweep.gsintelligence.token.GpsSignals { *; }
