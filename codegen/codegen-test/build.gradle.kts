dependencies {
    testImplementation(project(":codegen:codegen-erlang"))
    testImplementation(project(":codegen:codegen-elixir"))
    testImplementation(libs.smithy.model)
    testImplementation(libs.smithy.build)
    testImplementation(libs.smithy.protocol.tests)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
}

tasks.withType<Test>().configureEach {
    val updateSnapshots = project.findProperty("updateSnapshots")?.toString() ?: "false"
    systemProperty("updateSnapshots", updateSnapshots)
    // SnapshotTest writes goldens via raw Files.writeString — Gradle has no
    // visibility into those side effects, so when the user explicitly asks to
    // regenerate snapshots we must bypass the up-to-date check, otherwise
    // deleting src/test/resources/snapshots/ and re-running with
    // -PupdateSnapshots=true silently does nothing.
    if (updateSnapshots == "true") {
        outputs.upToDateWhen { false }
    }
}
