plugins {
    // Downloads a JDK 8 for the toolchain when none is installed, as pl_engine does.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.4.0"
}

rootProject.name = "cubrid-jdbc"
