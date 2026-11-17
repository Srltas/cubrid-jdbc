import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    java
    `maven-publish`
    signing
}

group = "org.cubrid"

// The serial cmake/gen_version.cmake wrote to output/VERSION-DIST (VERSION and the git commit count),
// or VERSION with serial 0000 when CMake has not run, e.g. right after a clone.
val versionDist = file("output/VERSION-DIST")
version = if (versionDist.exists()) versionDist.readText().trim() else file("VERSION").readText().trim() + ".0000"
val jdbcVersion = version.toString()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2") // brings hamcrest-core 1.3
}

// CUBRIDDriver.version_string holds @JDBC_DRIVER_VERSION_STRING@, so the compiler gets a copy of the
// sources with the version filled in.
val filterSources by tasks.registering(Copy::class) {
    from("src/jdbc") { include("**/*.java") }
    into(layout.buildDirectory.dir("filtered-src"))
    inputs.property("version", jdbcVersion)
    filteringCharset = "UTF-8"
    filter<ReplaceTokens>("tokens" to mapOf("JDBC_DRIVER_VERSION_STRING" to jdbcVersion))
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src/jdbc")) // what IDEs index and javadoc documents
        resources.setSrcDirs(emptyList<Any>())
    }
    test {
        java.setSrcDirs(listOf("src/test"))
        resources.setSrcDirs(emptyList<Any>())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.compileJava {
    setSource(filterSources)
}

// Besides the classes, the jars carry the java.sql.Driver service file and an empty
// CUBRID-JDBC-<version> marker at the root.
val jarExtras by tasks.registering {
    val dir = layout.buildDirectory.dir("jar-extras")
    inputs.property("version", jdbcVersion)
    outputs.dir(dir)
    doLast {
        val root = dir.get().asFile
        root.deleteRecursively()
        root.resolve("META-INF/services").mkdirs()
        root.resolve("META-INF/services/java.sql.Driver").writeText("cubrid.jdbc.driver.CUBRIDDriver")
        root.resolve("CUBRID-JDBC-$jdbcVersion").writeText("")
    }
}

// The engine packaging (CPack, build.sh -z jdbc) takes every cubrid-jdbc-*.jar in the project root,
// so a jar of an older serial left there would ship next to the new one.
val deleteStaleJars by tasks.registering(Delete::class) {
    delete(provider {
        projectDir.listFiles { f ->
            f.isFile && f.name.startsWith("cubrid-jdbc-") && f.name.endsWith(".jar") &&
                !f.name.startsWith("cubrid-jdbc-$jdbcVersion")
        }?.toList() ?: emptyList()
    })
}

// Created-By names the JDK that built the jar, as the Ant build wrote it; QA has checked it (CBRD-25401).
val buildJdk = javaToolchains.compilerFor(java.toolchain).get().metadata

// The engine (CPack, build.sh -z jdbc, pl_engine) takes the jars from the project root.
tasks.withType<Jar>().configureEach {
    destinationDirectory.set(layout.projectDirectory)
    dependsOn(deleteStaleJars)
    manifest.attributes("Created-By" to "${buildJdk.javaRuntimeVersion} (${buildJdk.vendor})")
}

tasks.jar {
    archiveFileName.set("cubrid-jdbc-$jdbcVersion.jar")
    from("src/jdbc/sql-error-codes.xml")
    from(jarExtras)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveFileName.set("cubrid-jdbc-$jdbcVersion-sources.jar")
    from(filterSources)
    from("src/jdbc/sql-error-codes.xml")
    from(jarExtras)
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        docEncoding = "UTF-8"
        charSet = "UTF-8"
        isAuthor = true
        isVersion = true
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveFileName.set("cubrid-jdbc-$jdbcVersion-javadoc.jar")
    from(tasks.javadoc)
}

// What CMake's jdbc_build target runs.
tasks.register("buildJdbc") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Builds the driver, sources and javadoc jars in the project root."
    dependsOn(tasks.jar, sourcesJar, javadocJar)
}

tasks.test {
    useJUnit()
    exclude("**/*IT.class") // the ITs need a live cluster: see integrationTest
}

// The load-balance ITs, run against a live cluster (src/test/cubrid/jdbc/lb/it/README.md). Every
// -Dlb.it.* given to Gradle reaches the tests; --tests picks a class.
val integrationTest by tasks.registering(Test::class) {
    description = "Runs the load-balance ITs against a live cluster."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnit()
    include("**/*IT.class")
    filter.isFailOnNoMatchingTests = false // without lb.it.enabled a class gated in @BeforeClass runs no test
    systemProperties(providers.systemPropertiesPrefixedBy("lb.it.").get())
    outputs.upToDateWhen { false } // the result depends on the cluster, not on the inputs
}

// Maven Central (org.cubrid:cubrid-jdbc). The upload is configured in gradle/central-publish.init.gradle,
// which only a release loads.
publishing {
    publications {
        create<MavenPublication>("jdbc") {
            artifactId = "cubrid-jdbc"
            artifact(tasks.jar)
            artifact(sourcesJar) { classifier = "sources" }
            artifact(javadocJar) { classifier = "javadoc" }
            pom {
                name.set("org.cubrid:cubrid-jdbc")
                description.set("JDBC for CUBRID Database")
                url.set("https://www.cubrid.org")
                licenses {
                    license {
                        name.set("The 3-Clause BSD License")
                        url.set("https://opensource.org/license/BSD-3-Clause")
                    }
                }
                developers {
                    developer {
                        name.set("CUBRID JDBC Developer Team")
                        email.set("cubrid-jdbc@cubrid.com")
                        organization.set("CUBRID")
                        organizationUrl.set("https://www.cubrid.org")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/CUBRID/cubrid-jdbc.git")
                    developerConnection.set("scm:git:ssh://github.com/CUBRID/cubrid-jdbc.git")
                    url.set("https://github.com/CUBRID/cubrid-jdbc")
                }
                issueManagement {
                    system.set("JIRA")
                    url.set("http://jira.cubrid.org/projects/APIS/issues")
                }
            }
        }
    }
    repositories {
        // build/staging-repo: what a release uploads, signed and with checksums, to look at beforehand
        maven {
            name = "localStaging"
            url = uri(layout.buildDirectory.dir("staging-repo"))
        }
    }
}

// The local gpg signs, as release.sh did (signing.gnupg.keyName and signing.gnupg.passphrase in
// ~/.gradle/gradle.properties); an ASCII-armored key in signingKey and signingPassword wins.
signing {
    val signingKey = providers.gradleProperty("signingKey").orNull
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingPassword").orNull)
    } else {
        useGpgCmd()
    }
    sign(publishing.publications["jdbc"])
}

// The same files as one zip, for an upload by hand in the Central Portal.
val centralBundle by tasks.registering(Zip::class) {
    dependsOn("publishJdbcPublicationToLocalStagingRepository")
    from(layout.buildDirectory.dir("staging-repo")) { include("org/cubrid/cubrid-jdbc/$jdbcVersion/**") }
    archiveFileName.set("cubrid-jdbc-$jdbcVersion-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central"))
}
