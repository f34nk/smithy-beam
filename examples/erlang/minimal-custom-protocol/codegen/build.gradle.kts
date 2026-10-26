plugins {
    java
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("io.github.f34nk.smithy.beam:codegen-erlang:0.1.0-SNAPSHOT")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
