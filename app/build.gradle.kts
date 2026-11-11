plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "top.qiuli55.fluxbot"
    compileSdk = 34

    defaultConfig {
        applicationId = "top.qiuli55.fluxbot"
        minSdk = 26
        targetSdk = 34
        versionCode = 27
        versionName = "0.3.15"

        // 云桌面连接参数不落源码：优先读环境变量，其次读 Gradle 属性，缺省为空。
        fun configValue(name: String): String =
            System.getenv(name) ?: project.findProperty(name)?.toString() ?: ""

        fun javaString(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        buildConfigField("String", "DESKTOP_HOST", javaString(configValue("FLUXBOT_DESKTOP_HOST")))
        buildConfigField("String", "DESKTOP_TOKEN", javaString(configValue("FLUXBOT_DESKTOP_TOKEN")))
        buildConfigField("String", "DESKTOP_BRIDGE", javaString(configValue("FLUXBOT_DESKTOP_BRIDGE")))
        buildConfigField("String", "DESKTOP_BRIDGE_KEY", javaString(configValue("FLUXBOT_DESKTOP_BRIDGE_KEY")))
    }

    // 发布签名：CI 用仓库 secret 还原 keystore 后通过环境变量注入。
    // 本地没设置时保持 debug 签名，assembleDebug / assembleRelease 都能直接跑。
    val releaseKeystore = System.getenv("FLUXBOT_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("FLUXBOT_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("FLUXBOT_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("FLUXBOT_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        // debug 也走同一套签名：CI 的调试密钥每次随机生成，会导致新旧包无法覆盖安装。
        debug {
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
