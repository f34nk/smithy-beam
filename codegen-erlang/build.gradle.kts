import org.gradle.api.tasks.Copy

dependencies {
    api(project(":codegen-core"))
    implementation(project(":protocol-analyzer"))
    testImplementation(rootProject.libs.smithy.aws.traits)
}

tasks.named<Copy>("processResources") {
    from(rootProject.layout.projectDirectory.dir("runtime-erlang")) {
        into("META-INF/smithy-beam/runtime/erlang")
    }
}
