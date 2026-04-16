import org.gradle.api.tasks.Copy

dependencies {
    api(project(":codegen-core"))
    implementation(project(":protocol-analyzer"))
    testImplementation(rootProject.libs.junit.api)
    testImplementation(rootProject.libs.assertj)
    testImplementation(rootProject.libs.smithy.aws.traits)
    testRuntimeOnly(rootProject.libs.junit.engine)
}

tasks.named<Copy>("processResources") {
    from(rootProject.layout.projectDirectory.dir("runtime-elixir")) {
        into("META-INF/smithy-beam/runtime/elixir")
    }
}
