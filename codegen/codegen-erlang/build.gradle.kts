dependencies {
    api(project(":codegen:codegen-core"))
    api(libs.smithy.codegen.core)
    api(libs.smithy.aws.traits)
}

// Bundle hand-written Erlang runtime modules into the JAR.
// ErlangRuntimeIntegration copies only referenced files to the FileManifest.
tasks.processResources {
    from("$rootDir/runtime-erlang") {
        into("META-INF/smithy-beam/runtime/erlang")
    }
}
