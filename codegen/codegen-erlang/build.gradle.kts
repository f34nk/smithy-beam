dependencies {
    api(project(":codegen:codegen-core"))
    api(project(":codegen:codegen-ir"))
    api(libs.beam.ir)
    api(libs.smithy.model)
    api(libs.smithy.codegen.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
