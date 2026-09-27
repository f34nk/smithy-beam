description = "BEAM language DSL helpers used by smithy-beam code generators"
extra["pomName"] = "smithy-beam beam-lang"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
