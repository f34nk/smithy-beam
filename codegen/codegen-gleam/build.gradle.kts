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

tasks.processResources {
    from(rootProject.file("runtime/gleam")) {
        into("runtime/gleam")
        exclude("build/**")
        exclude("test/**")
        exclude(".git/**")
    }
}
