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

/**
 * The AirPlay receiver is a native library (app/src/main/cpp, UxPlay through
 * jqssun/android-airplay-server) that needs NDK 27.1.12297006 and CMake 3.22.1.
 * CI has them. A machine without them can still compile the Java side and run
 * the unit tests with -Pveil.skipNative=true; that APK cannot receive AirPlay.
 */
val skipNative = (findProperty("veil.skipNative") as String?) == "true"

android {
    namespace = "app.veil"
    compileSdk = 36
    if (!skipNative) {
        ndkVersion = "27.1.12297006"
    }

    defaultConfig {
        applicationId = "app.veil.zeekr"
        minSdk = 28
        targetSdk = 36
        // Bump both before every v* tag: versionCode decides whether the car
        // accepts the update over the old one, versionName becomes the release name.
        versionCode = 1
        versionName = "0.1.0-alpha"

        // The 7X head unit is arm64; the AirPlay native library is built for it only.
        ndk { abiFilters += "arm64-v8a" }
        if (!skipNative) {
            externalNativeBuild {
                cmake {
                    arguments += "-DANDROID_STL=c++_shared"
                    arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                }
            }
        }
    }

    if (!skipNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
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
        // Oboe's headers and library come through its prefab AAR
        prefab = !skipNative
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

}

if (!releaseKeyAvailable) {
    logger.warn("Veil: no release key (VEIL_KEYSTORE); the release APK will be unsigned.")
}
if (skipNative) {
    logger.warn("Veil: veil.skipNative=true; the AirPlay native library is left out of this build.")
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

    // AirPlay audio output (the native library links it through prefab)
    implementation(libs.oboe)

    testImplementation(libs.junit)
}
