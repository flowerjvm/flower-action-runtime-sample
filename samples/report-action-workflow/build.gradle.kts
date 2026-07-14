plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Spring Boot sample that steps through the flower-action-runtime workflow backend."

dependencies {
    implementation(project(":runtime-core-local"))
    implementation(project(":runtime-workflow-local"))
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
