import io.github.gradlenexus.publishplugin.NexusPublishExtension
import java.util.Base64

plugins {
    kotlin("jvm") version "1.9.25"
    id("com.ncorti.ktfmt.gradle") version "0.22.0"
    id("signing")
    id("maven-publish")
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0" apply false
}

if (project == rootProject) {
    apply(plugin = "io.github.gradle-nexus.publish-plugin")

    configure<NexusPublishExtension> {
        repositories {
            sonatype {
                nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
                snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
                username.set(System.getenv("SONATYPE_USERNAME"))
                password.set(System.getenv("SONATYPE_PASSWORD"))
            }
        }
    }
}

apply("versions.gradle.kts")

group = "io.github.witomlin"

version = "1.0.0"

java {
    withSourcesJar()
    withJavadocJar()

    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
    api("org.slf4j:slf4j-api:${project.extra["slf4jVersion"]}")

    api(platform("io.micrometer:micrometer-bom:${project.extra["micrometerBomVersion"]}"))
    api("io.micrometer:micrometer-core")

    testImplementation("io.kotest:kotest-runner-junit5:${project.extra["kotestVersion"]}")
    testImplementation("io.kotest:kotest-assertions-core:${project.extra["kotestVersion"]}")
    testImplementation("io.kotest:kotest-extensions-now:${project.extra["kotestVersion"]}")
    testImplementation("io.mockk:mockk:${project.extra["mockkVersion"]}")
}

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
}

ktfmt {
    kotlinLangStyle()
    maxWidth.set(120)
}

signing {
    useInMemoryPgpKeys(
        String(Base64.getDecoder().decode(System.getenv("GPG_SECRET_KEY") ?: "")),
        System.getenv("GPG_PASSWORD"),
    )
    sign(publishing.publications)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // Auto: groupId: project.group, artifactId: project.name, version: project.version

            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set(rootProject.name)
                description.set("${rootProject.name} $version: real-time sliding window implementations for Kotlin")
                url.set("https://github.com/witomlin/${rootProject.name}")

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
                    connection.set("scm:git:git://github.com/witomlin/${rootProject.name}.git")
                    developerConnection.set("scm:git:ssh://git@github.com/witomlin/${rootProject.name}.git")
                    url.set("https://github.com/witomlin/${rootProject.name}")
                }
            }
        }
    }
}
