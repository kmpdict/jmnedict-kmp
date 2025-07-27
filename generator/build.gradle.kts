plugins {
    id("java-gradle-plugin")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

gradlePlugin {
    plugins {
        create("com.boswelja.jmnedict") {
            id = "com.boswelja.jmnedict.generator"
            implementationClass = "com.boswelja.jmnedict.generator.JmneDictGeneratorPlugin"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlin.plugin)
    implementation(libs.compose.gradlePlugin)

    implementation(libs.boswelja.xmldtd)
    implementation(libs.kotlinx.io.core)
    implementation(libs.okio.core)
    implementation(libs.okio.zstd)

    testImplementation(libs.kotlin.test)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("${rootDir.parent}/config/detekt.yml")
    basePath = rootDir.absolutePath
}
