plugins {
    `java-library`
}

description = "Local source bridge for flower-action-runtime-core while artifacts are unpublished."

sourceSets {
    main {
        java.srcDir(rootProject.file("../flower-action-runtime/flower-action-runtime-core/src/main/java"))
    }
}
