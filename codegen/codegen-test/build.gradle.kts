dependencies {
    testImplementation(project(":codegen:codegen-core"))
    testImplementation(project(":codegen:codegen-erlang"))
    testImplementation(project(":codegen:codegen-elixir"))
    testImplementation(libs.smithy.model)
    testImplementation(libs.smithy.codegen.core)
    testImplementation(libs.smithy.build)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
