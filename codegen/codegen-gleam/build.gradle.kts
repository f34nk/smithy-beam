description = "smithy-beam - Smithy build plugins for Gleam client, server, and types codegen"
extra["pomName"] = "smithy-beam codegen-gleam"

dependencies {
    api(project(":codegen:codegen-core"))
    api(project(":beam-lang"))
    api(libs.smithy.model)
    api(libs.smithy.codegen.core)
    api(libs.smithy.build)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Runtime packaging (runtime/gleam) is added when the Gleam runtime exists.
tasks.processResources {
}
