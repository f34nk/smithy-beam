rootProject.name = "smithy-beam"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "codegen-core",
    "codegen-protocols",
    "codegen-erlang",
    "codegen-elixir"
)
