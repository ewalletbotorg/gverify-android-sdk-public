# Gamma Verify Intelligence SDK — consumer ProGuard/R8 rules.
# Keep the public API surface and token data classes intact under obfuscation.
-keep public class com.gammaverify.gvintelligence.GV { public *; }
-keep public class com.gammaverify.gvintelligence.GSConfig { *; }
-keep public class com.gammaverify.gvintelligence.GpsMode { *; }
-keep public class com.gammaverify.gvintelligence.GSException { *; }
-keep public class com.gammaverify.gvintelligence.token.GpsSignals { *; }
