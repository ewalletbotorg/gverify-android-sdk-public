plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

val groupName: String by lazy { (project.findProperty("GROUP") as String?) ?: "com.gammaverify" }
val versionName: String by lazy { (project.findProperty("VERSION_NAME") as String?) ?: "1.0.0" }

android {
    namespace = "com.gammaverify.gvintelligence"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // The only third-party dependency: Kotlin coroutines, the idiomatic analog
    // of Swift's structured concurrency used by the iOS SDK. Everything else is
    // Android framework + java.security / org.json (built in).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // org.json is stubbed (returns null) in local unit tests, so bring the real
    // implementation onto the test classpath for JSON-shape assertions.
    testImplementation("org.json:json:20240303")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = groupName
                artifactId = "gvintelligence"
                version = versionName
            }
        }
    }
}
