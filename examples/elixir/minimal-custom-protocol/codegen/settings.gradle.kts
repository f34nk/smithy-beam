pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "minimal-custom-protocol-elixir-codegen"

includeBuild("../../../../") {
    dependencySubstitution {
        substitute(module("io.smithy.beam:codegen-elixir")).using(project(":codegen:codegen-elixir"))
        substitute(module("io.smithy.beam:codegen-core")).using(project(":codegen:codegen-core"))
    }
}
