plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp") }
android {
 namespace = "com.qingluan.ledger"
 compileSdk = 35
 defaultConfig { applicationId = "com.qingluan.ledger"; minSdk = 26; targetSdk = 35; versionCode = 108; versionName = "1.0.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.navigation:navigation-compose:2.8.5")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 ksp("androidx.room:room-compiler:2.6.1")
 implementation("androidx.datastore:datastore-preferences:1.1.1")
 implementation("androidx.exifinterface:exifinterface:1.4.1")
 implementation("dev.chrisbanes.haze:haze:1.1.1")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
 testImplementation("org.robolectric:robolectric:4.17")
 testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 testImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
// Android 16 Robolectric requires Java 21; optional override for a separate build JDK.
tasks.withType<Test>().configureEach {
 jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
 providers.gradleProperty("testJavaExecutable").orNull?.let { executable = it }
}
