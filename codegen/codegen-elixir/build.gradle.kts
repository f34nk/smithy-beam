dependencies {
    api(project(":codegen:codegen-core"))
    api(libs.smithy.codegen.core)
    api(libs.smithy.aws.traits)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
}

// Bundle hand-written Elixir runtime modules into the JAR.
// ElixirRuntimeIntegration copies only referenced files to the FileManifest.
tasks.processResources {
    from("$rootDir/runtime-elixir") {
        into("META-INF/smithy-beam/runtime/elixir")
    }
}
