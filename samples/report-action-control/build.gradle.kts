plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Spring Boot sample that visualizes the flower-action-runtime controlled action pipeline."

dependencies {
    implementation("io.github.flowerjvm:flower-action-runtime-core:0.3.1")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
