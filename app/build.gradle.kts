plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val llmConfigFile = rootProject.file("llm_config.json")
if (!llmConfigFile.exists()) {
    throw GradleException("缺少 llm_config.json,请参考 llm_config.example.json 创建本地配置.")
}

val llmConfig = try {
    @Suppress("UNCHECKED_CAST")
    groovy.json.JsonSlurper().parse(llmConfigFile) as Map<String, Any?>
} catch (error: Exception) {
    throw GradleException("llm_config.json 格式无效.", error)
}

fun requiredApiKey(name: String): String {
    return (llmConfig[name] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("llm_config.json 缺少非空字段 $name.")
}

fun String.asBuildConfigValue(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.android.androidAndroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.android.androidAndroid"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEEPSEEK_API_KEY", requiredApiKey("deepseekApiKey").asBuildConfigValue())
        buildConfigField("String", "MOONSHOT_API_KEY", requiredApiKey("moonshotApiKey").asBuildConfigValue())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.openai:openai-java:4.29.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
}
