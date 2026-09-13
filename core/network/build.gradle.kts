plugins {
    id("wlo.android.library")
    alias(libs.plugins.kotlin.serialization) // OFF v2 wire types (ADR-004 rules)
}

dependencies {
    // The choke point's own stack (D9 allowlist: ktor/okhttp live HERE only).
    implementation(libs.ktor.client.core)
    // ADR-004: the OkHttp engine (the Android engine lacks HTTP/2).
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.okio)

    // Ports + consent abstractions (dependency inversion: this module implements
    // EgressPort and consumes ConsentGate — never the reverse).
    api(project(":core:ports"))
    implementation(project(":core:consent"))
    implementation(project(":core:database"))
    // Room types surface through WloDatabase (:core:database keeps its own
    // runtime dep internal).
    implementation(libs.androidx.room3.runtime)
    implementation(libs.kotlinx.coroutines.core)

    // JVM unit tests run the REAL client against a localhost MockWebServer.
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
}
