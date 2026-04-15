dependencies {
    api(project(":codegen-core"))
    api(rootProject.libs.smithy.aws.traits)
    testImplementation(rootProject.libs.smithy.build)
}
