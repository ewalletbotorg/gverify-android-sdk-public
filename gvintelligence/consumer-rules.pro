# Gamma Verify Intelligence SDK — consumer ProGuard/R8 rules.
# Keep the public API surface and token data classes intact under obfuscation.
-keep public class com.gammasweep.gvintelligence.GS { public *; }
-keep public class com.gammasweep.gvintelligence.GSConfig { *; }
-keep public class com.gammasweep.gvintelligence.GpsMode { *; }
-keep public class com.gammasweep.gvintelligence.GSException { *; }
-keep public class com.gammasweep.gvintelligence.token.GpsSignals { *; }
