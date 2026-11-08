import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.haofan.xiaoxinxin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.haofan.xiaoxinxin"
        minSdk = 26
        // targetSdk 暂留 35：API 36 强制 edge-to-edge，先把岛跑通再单独做视觉回归
        targetSdk = 35
        versionCode = 43
        versionName = "2.6.2"
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        create("release") {
            val propFile = rootProject.file("keystore.properties")
            if (propFile.exists()) {
                val props = Properties().apply {
                    propFile.inputStream().use { load(it) }
                }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        // 尝鲜版直接以 debug 构建分发（debuggable + debug 签名），但包名和版本号不再挂 .debug 后缀，
        // 这样从尝鲜版走到正式版时包名不变、能原地升级，不用卸载重装。
        // 正式发版前只需补 keystore.properties，跑 assembleRelease 即可。
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
        buildConfig = true   // 设置页版本号读 BuildConfig.VERSION_NAME，避免手抄版本号写歪
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
}
