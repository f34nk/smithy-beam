dependencies {
    api(project(":codegen-core"))
    implementation(project(":protocol-analyzer"))
    testImplementation(rootProject.libs.junit.api)
    testImplementation(rootProject.libs.assertj)
    testRuntimeOnly(rootProject.libs.junit.engine)
}
