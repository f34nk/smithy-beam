dependencies {
    api(project(":codegen:codegen-core"))
    api(libs.smithy.codegen.core)
    api(libs.smithy.aws.traits)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
}

tasks.processResources {
    from("$rootDir/runtime-erlang") {
        into("META-INF/smithy-beam/runtime/erlang")
    }
}
