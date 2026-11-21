import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

val amazonStore = providers.gradleProperty("billmivoAmazon").orNull == "true"
val amazonSandbox = providers.gradleProperty("billmivoAmazonSandbox").orNull == "true"
val verifierEndpoint = providers.gradleProperty("billmivoVerifierUrl").orNull.orEmpty()
if (verifierEndpoint.isNotBlank()) {
    val endpoint = URI(verifierEndpoint)
    require(endpoint.scheme == "https" && endpoint.host != null && endpoint.userInfo == null &&
        endpoint.rawQuery == null && endpoint.rawFragment == null) { "Use a clean HTTPS receipt verification URL." }
}

android {
    namespace = "com.billmivo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.billmivo.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 13
        versionName = "0.12.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AMAZON_VERIFIER", "\"$verifierEndpoint\"")
        buildConfigField("boolean", "AMAZON_SANDBOX", amazonSandbox.toString())
    }

    buildFeatures { compose = true; buildConfig = true }
    sourceSets {
        getByName("main").java.srcDir(if (amazonStore) "src/amazon/kotlin" else "src/offline/kotlin")
        if (amazonStore) {
            getByName("main").assets.srcDir("src/amazon/assets")
            getByName("debug").manifest.srcFile("src/amazon/AndroidManifest.xml")
            getByName("release").manifest.srcFile("src/amazon/AndroidManifest.xml")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

if (amazonStore) {
    tasks.matching { it.name == "preReleaseBuild" }.configureEach {
        doFirst {
            check(!amazonSandbox && verifierEndpoint.isNotBlank() &&
                file("src/amazon/assets/AppstoreAuthenticationKey.pem").isFile) {
                "Amazon release requires the Appstore public key and a production receipt verification URL."
            }
        }
    }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    if (amazonStore) implementation("com.amazon.device:amazon-appstore-sdk:3.0.9")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
