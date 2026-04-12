import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.testing.Test

group = "io.smithy.beam"
version = "0.1.0"

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension>("java") {
        toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    }
    repositories { mavenCentral() }
    dependencies {
        add("testImplementation", rootProject.libs.junit.api)
        add("testRuntimeOnly", rootProject.libs.junit.engine)
        add("testImplementation", rootProject.libs.assertj)
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    extensions.configure<PublishingExtension>("publishing") {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}
