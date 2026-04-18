dependencies {
    api(libs.smithy.model)
    api(libs.smithy.build)
    api(libs.smithy.codegen.core)
    api(libs.smithy.aws.traits)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
}
