// Root build for flower-action-runtime sample applications.
//
// Each sample lives under samples/<name>. The root only contributes shared
// versions, repositories, the Java toolchain, and common test configuration.

plugins {
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "io.github.flowerjvm.flower.action.runtime.samples"
    version = "0.3.2"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    the<JavaPluginExtension>().toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
