plugins {
    `kotlin-dsl`
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

dependencies {
    implementation(libs.kotlinGradlePlugin)
}