plugins {
    `java-library`
}

description = "Local source bridge for flower-action-runtime-workflow while artifacts are unpublished."

sourceSets {
    main {
        java.srcDir(rootProject.file("../flower-action-runtime/flower-action-runtime-workflow/src/main/java"))
    }
}

dependencies {
    api(project(":runtime-core-local"))
    api("io.github.parkkevinsb.flower:flower-core:0.1.0-SNAPSHOT")
}
