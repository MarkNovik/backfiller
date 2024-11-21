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
    implementation("org.jetbrains.compose.material3:material3:1.3.1")
    implementation("com.darkrockstudios:mpfilepicker:3.1.0")
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
                includeAllModules = true
            }
            packageName = "backfiller"
            packageVersion = "1.0.0"
        }
    }
}
