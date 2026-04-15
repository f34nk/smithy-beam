rootProject.name = "smithy-beam"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "codegen-core",
    "protocol-analyzer",
    "codegen-erlang",
    "codegen-elixir"
)
