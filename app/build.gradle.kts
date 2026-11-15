plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.paperwork.pwxkqm"
    minSdk = 28
    targetSdk = 36
    versionCode = 2
    versionName = "2.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val storePath = System.getenv("SIGNING_STORE_FILE")
      val storePasswordValue = System.getenv("SIGNING_STORE_PASSWORD")
      val aliasValue = System.getenv("SIGNING_KEY_ALIAS")
      val keyPasswordValue = System.getenv("SIGNING_KEY_PASSWORD")
      if (!storePath.isNullOrBlank() && !storePasswordValue.isNullOrBlank() &&
        !aliasValue.isNullOrBlank() && !keyPasswordValue.isNullOrBlank()
      ) {
        storeFile = file(storePath)
        storePassword = storePasswordValue
        keyAlias = aliasValue
        keyPassword = keyPasswordValue
      }
    }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val signingConfigured = listOf(
        System.getenv("SIGNING_STORE_FILE"),
        System.getenv("SIGNING_STORE_PASSWORD"),
        System.getenv("SIGNING_KEY_ALIAS"),
        System.getenv("SIGNING_KEY_PASSWORD")
      ).all { !it.isNullOrBlank() }
      if (signingConfigured) signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

val validateProductionSigning by tasks.registering {
  doLast {
    val required = listOf("SIGNING_STORE_FILE", "SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD")
    check(required.all { !System.getenv(it).isNullOrBlank() }) {
      "Release signing requires SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD. No debug-key fallback is allowed."
    }
    check(file(System.getenv("SIGNING_STORE_FILE")).isFile) { "The release signing file does not exist." }
  }
}
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
  dependsOn(validateProductionSigning)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0")
  implementation("androidx.compose.material3.adaptive:adaptive-layout:1.1.0")
  implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.1.0")
  implementation(libs.androidx.compose.material3.window.size)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)

  implementation(libs.pdfbox.android)
  constraints {
    implementation("org.bouncycastle:bcprov-jdk15to18:1.87")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.87")
    implementation("org.bouncycastle:bcutil-jdk15to18:1.87")
  }
  implementation(libs.play.services.mlkit.document.scanner)
  implementation(libs.play.services.mlkit.text.recognition)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

dependencyLocking { lockAllConfigurations() }

tasks.register("writeRuntimeDependencyInventory") {
  val runtime = configurations.named("releaseRuntimeClasspath")
  val report = layout.buildDirectory.file("reports/runtime-dependencies.tsv")
  outputs.file(report)
  doLast {
    val rows = runtime.get().resolvedConfiguration.resolvedArtifacts.map {
      val id = it.moduleVersion.id
      "${id.group}:${id.name}\t${id.version}"
    }.distinct().sorted()
    report.get().asFile.apply { parentFile.mkdirs(); writeText(rows.joinToString("\n") + "\n") }
  }
}
