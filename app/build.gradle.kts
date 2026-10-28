import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Release signing: your own key, never a public test key.
 *
 * The four values come from the environment (CI injects them from repository
 * secrets) or, on this machine, from the git-ignored local.properties:
 *   VEIL_KEYSTORE / VEIL_KEYSTORE_PASSWORD / VEIL_KEY_ALIAS / VEIL_KEY_PASSWORD
 * With none of them set the release APK is built unsigned, and the build says so.
 */
fun signingValue(name: String): String {
    val fromEnv = System.getenv(name)?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv
    val propsFile = rootProject.file("local.properties")
    if (!propsFile.isFile) return ""
    val props = Properties()
    propsFile.inputStream().use { props.load(it) }
    return props.getProperty(name)?.trim().orEmpty()
}

val keystorePath = signingValue("VEIL_KEYSTORE")
val releaseKeyAvailable = keystorePath.isNotEmpty() && file(keystorePath).exists()

android {
    namespace = "app.veil"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.veil.zeekr"
        minSdk = 28
        targetSdk = 36
        // Bump both before every v* tag: versionCode decides whether the car
        // accepts the update over the old one, versionName becomes the release name.
        versionCode = 1
        versionName = "0.1.0-alpha"

        // The 7X head unit is arm64; the AirPlay native library (step 3) is built for it only.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (releaseKeyAvailable) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signingValue("VEIL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("VEIL_KEY_ALIAS")
                keyPassword = signingValue("VEIL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (releaseKeyAvailable) signingConfigs.getByName("release") else null
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

}

if (!releaseKeyAvailable) {
    logger.warn("Veil: no release key (VEIL_KEYSTORE); the release APK will be unsigned.")
}

/**
 * The About page shows LICENSE and NOTICE.md straight from the repository root, so
 * there is one copy of each and the APK cannot drift from what the repository says.
 */
abstract class CopyLegalAssets : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        sources.files.forEach { it.copyTo(File(out, it.name), overwrite = true) }
    }
}

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<CopyLegalAssets>("copyLegalAssets$suffix") {
            sources.from(rootProject.file("LICENSE"), rootProject.file("NOTICE.md"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, CopyLegalAssets::outputDir)
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.fragment)
    implementation(libs.recyclerview)

    testImplementation(libs.junit)
}
