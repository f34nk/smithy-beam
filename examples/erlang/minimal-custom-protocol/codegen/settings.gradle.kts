pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "minimal-custom-protocol-codegen"

includeBuild("../../../../") {
    dependencySubstitution {
        substitute(module("io.smithy.beam:codegen-erlang")).using(project(":codegen:codegen-erlang"))
        substitute(module("io.smithy.beam:codegen-core")).using(project(":codegen:codegen-core"))
    }
}
