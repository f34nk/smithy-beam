dependencies {
    api(libs.smithy.model)
    api(libs.smithy.codegen.core)
    api(libs.smithy.aws.traits)
    implementation(libs.smithy.rules.engine)
    implementation(libs.smithy.aws.endpoints)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.smithy.aws.traits)
    testImplementation(libs.assertj.core)
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
