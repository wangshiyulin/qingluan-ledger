import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp") }
android {
 namespace = "com.qingluan.ledger"
 compileSdk = 35
 defaultConfig { applicationId = "com.qingluan.ledger"; minSdk = 26; targetSdk = 35; versionCode = 111; versionName = "1.1.1"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = true
   isShrinkResources = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
  }
 }
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
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.android.tools.build:apksig:8.7.3")
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

// Keep the same public filename for debug/release; their output directories remain separate.
android.applicationVariants.all {
    val ledgerVersion = versionName
    outputs.all {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "ledger-v$ledgerVersion.apk"
    }
}

// Physical alpha corners also survive APK compatibility layers that ignore adaptive masks.
abstract class GenerateCompatIcons : DefaultTask() {
    @get:InputFile abstract val brandSource: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun generate() {
        val original = ImageIO.read(brandSource.get().asFile) ?: error("Invalid brand image")
        val output = outputDirectory.get().asFile
        fun icon(size: Int, name: String) {
            val target = output.resolve(name).apply {parentFile.mkdirs()}
            val bitmap = BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB)
            val canvas = bitmap.createGraphics()
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                canvas.fill(RoundRectangle2D.Double(0.0,0.0,size.toDouble(),size.toDouble(),size*.44,size*.44))
                canvas.composite=AlphaComposite.SrcIn
                canvas.drawImage(original,0,0,size,size,null)
            } finally {canvas.dispose()}
            check(ImageIO.write(bitmap,"png",target))
        }
        icon(768,"drawable-nodpi/brand_rounded.png")
        mapOf("mdpi" to 48,"hdpi" to 72,"xhdpi" to 96,"xxhdpi" to 144,"xxxhdpi" to 192).forEach { (density,size) ->
            icon(size,"mipmap-$density/ic_launcher.png")
            icon(size,"mipmap-$density/ic_launcher_round.png")
        }
    }
}
val compatIcons = tasks.register<GenerateCompatIcons>("generateCompatIcons") {
    brandSource.set(layout.projectDirectory.file("src/main/res/drawable-nodpi/brand_logo.png"))
    outputDirectory.set(layout.buildDirectory.dir("generated/compat-icons/res"))
}
androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(compatIcons,GenerateCompatIcons::outputDirectory)
}
