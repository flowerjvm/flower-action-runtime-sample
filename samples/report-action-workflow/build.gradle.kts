plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Spring Boot sample that steps through the flower-action-runtime workflow backend."

dependencies {
    implementation("io.github.flowerjvm:flower-core:0.1.1")
    implementation("io.github.flowerjvm:flower-action-runtime-core:0.3.1")
    implementation("io.github.flowerjvm:flower-action-runtime-workflow:0.3.1")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
