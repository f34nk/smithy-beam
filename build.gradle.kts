import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

spotless {
    isEnforceCheck = false
    java {
        target(
            "beam-lang/src/**/*.java",
            "codegen/**/src/**/*.java",
            "examples/**/codegen/src/**/*.java")
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    repositories {
        mavenLocal()
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Javadoc>().configureEach {
        // First publish: do not fail the build on missing javadoc tags.
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        isFailOnError = false
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    if (name != "codegen-test") {
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])

                    pom {
                        name.set(
                            project.provider {
                                (project.findProperty("pomName") as String?)
                                    ?: project.name
                            }
                        )
                        description.set(
                            project.provider {
                                project.description
                                    ?: "Smithy code generator components for BEAM languages"
                            }
                        )
                        url.set("https://github.com/f34nk/smithy-beam")
                        licenses {
                            license {
                                name.set("Apache License 2.0")
                                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
                            }
                        }
                        developers {
                            developer {
                                id.set("f34nk")
                                name.set("Frank Eickhoff")
                                url.set("https://github.com/f34nk")
                            }
                        }
                        scm {
                            url.set("https://github.com/f34nk/smithy-beam")
                            connection.set("scm:git:https://github.com/f34nk/smithy-beam.git")
                            developerConnection.set(
                                "scm:git:ssh://git@github.com/f34nk/smithy-beam.git"
                            )
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "localStaging"
                    url = rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
                }
            }
        }
    } else {
        tasks.configureEach {
            if (name.startsWith("publish")) {
                enabled = false
            }
        }
    }
}
