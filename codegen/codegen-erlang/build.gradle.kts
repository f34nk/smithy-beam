dependencies {
    api(project(":codegen:codegen-core"))
    api(project(":beam-dsl"))
    api(libs.smithy.model)
    api(libs.smithy.codegen.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from(rootProject.file("runtime/erlang")) {
        into("runtime/erlang")
        exclude("test/**")
        exclude("_build/**")
        exclude("rebar.lock")
    }
}
