plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinSerialization)
    application
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)
}

application {
    mainClass.set("uz.ictschool.sketchchain.server.ApplicationKt")
}
