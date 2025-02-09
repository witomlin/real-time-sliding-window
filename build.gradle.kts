import org.jetbrains.kotlin.gradle.dsl.*
import org.jreleaser.model.Active
import org.jreleaser.model.Http

val gradleProject = project

plugins {
    kotlin("jvm") version "1.9.25"
    id("com.ncorti.ktfmt.gradle") version "0.22.0"
    id("org.jetbrains.dokka") version "2.0.0"
    id("org.jreleaser") version "1.16.0"
    id("maven-publish")
}

apply("versions.gradle.kts")

group = "io.github.witomlin"

version = "1.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) } // Gradle toolchain
    withSourcesJar()
    withJavadocJar()
}

repositories { mavenCentral() }

dependencies {
    api("org.slf4j:slf4j-api:${gradleProject.extra["slf4jVersion"]}")

    api(platform("io.micrometer:micrometer-bom:${gradleProject.extra["micrometerBomVersion"]}"))
    api("io.micrometer:micrometer-core")

    testImplementation("io.kotest:kotest-runner-junit5:${gradleProject.extra["kotestVersion"]}")
    testImplementation("io.kotest:kotest-assertions-core:${gradleProject.extra["kotestVersion"]}")
    testImplementation("io.kotest:kotest-extensions-now:${gradleProject.extra["kotestVersion"]}")
    testImplementation("io.mockk:mockk:${gradleProject.extra["mockkVersion"]}")
}

kotlin {
    compilerOptions {
        // Java
        jvmToolchain(11) // Note: already set via Java toolchain but being verbose
        jvmTarget.set(JvmTarget.JVM_11) // Note: already set via Java toolchain but being verbose

        // Kotlin
        apiVersion.set(KotlinVersion.KOTLIN_1_8)
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

ktfmt {
    kotlinLangStyle()
    maxWidth.set(120)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
}

tasks.named("build") {
    doLast {
        val jReleaserDir = file("${layout.buildDirectory.get()}/jreleaser")
        if (!jReleaserDir.exists()) jReleaserDir.mkdirs()

        val mavenStagingDir = file("${layout.buildDirectory.get()}/maven-staging")
        if (!mavenStagingDir.exists()) mavenStagingDir.mkdirs()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // Auto: groupId: project.group, artifactId: project.name, version: project.version
            from(components["java"])

            pom {
                name.set(gradleProject.name)
                description.set("Real-time sliding window implementations for Kotlin")
                url.set("https://github.com/witomlin/${gradleProject.name}")

                licenses {
                    license {
                        name.set("Apache License Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("witomlin")
                        name.set("Will Tomlin")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/witomlin/${gradleProject.name}.git")
                    developerConnection.set("scm:git:ssh://git@github.com/witomlin/${gradleProject.name}.git")
                    url.set("https://github.com/witomlin/${gradleProject.name}")
                }
            }
        }
    }

    repositories { maven { url = layout.buildDirectory.dir("maven-staging").get().asFile.toURI() } }
}

jreleaser {
    signing {
        active.set(Active.ALWAYS)
        armored.set(true)
    }

    project {
        name.set(gradleProject.name)
        description.set("Real-time sliding window implementations for Kotlin")
        version.set(gradleProject.version.toString())
        versionPattern.set("SEMVER")
        authors.set(listOf("Will Tomlin"))
        license.set("Apache License Version 2.0")
        copyright.set("2025 Will Tomlin")
        links.homepage.set("https://github.com/witomlin/${gradleProject.name}")
        links.bugTracker.set("https://github.com/witomlin/${gradleProject.name}/issues")
        links.license.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
    }

    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active.set(Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepositories.set(listOf("${layout.buildDirectory.get()}/maven-staging"))
                    snapshotSupported.set(false)
                    applyMavenCentralRules.set(true)
                    authorization.set(Http.Authorization.BASIC)
                }
            }
        }
    }
}
