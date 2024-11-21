import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "me.mark"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation("org.jetbrains.compose.material3:material3:1.3.1")
    implementation("com.darkrockstudios:mpfilepicker:3.1.0")
}

compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "me.mark"
    customDirectory(
        sourceSetName = "main",
        directoryProvider = provider { layout.projectDirectory.dir("/src/main/resources") }
    )
}

compose.desktop {
    application {
        mainClass = "MainKt"

        buildTypes.release.proguard {
            optimize.set(true)
            configurationFiles.from(project.file("./rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            windows {
                modules("com.luciad.imageio.webp")
                includeAllModules = true
                iconFile.set(project.file("./src/main/resources/drawable/icon.ico"))
            }
            packageName = "backfiller"
            packageVersion = "1.0.0"
        }
    }
}
