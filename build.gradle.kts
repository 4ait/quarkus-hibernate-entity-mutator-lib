import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jreleaser.model.Active

group = "ru.code4a"
version = file("version").readText().trim()

plugins {
  val kotlinVersion = "2.4.10"

  kotlin("jvm") version kotlinVersion
  kotlin("plugin.serialization") version kotlinVersion
  // Test entities must be open and have a no-arg constructor, as entities of Quarkus applications do.
  kotlin("plugin.allopen") version kotlinVersion
  kotlin("plugin.jpa") version kotlinVersion

  id("org.kordamp.gradle.jandex") version "2.1.0"
  // Tests run against entities enhanced the same way Quarkus enhances them.
  id("org.hibernate.orm") version "7.4.9.Final"

  `java-library`
  `maven-publish`
  id("org.jreleaser") version "1.21.0"
}

java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25

  withJavadocJar()
  withSourcesJar()
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_25
  }
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      artifactId = "quarkus-hibernate-entity-mutator"

      from(components["java"])

      pom {
        name = "Quarkus Hibernate Entity Mutator Library"
        description =
          "This extension provides a convenient and type-safe way to manage bidirectional JPA " +
            "relationships in your Quarkus applications. " +
            "It automatically detects entity associations at build time and generates " +
            "appropriate mutators to ensure both sides of relationships stay in sync."
        url = "https://github.com/4ait/quarkus-hibernate-entity-mutator-lib"
        inceptionYear = "2025"
        licenses {
          license {
            name = "The Apache License, Version 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
          }
        }
        developers {
          developer {
            id = "tikara"
            name = "Evgeniy Simonenko"
            email = "tiikara93@gmail.com"
            organization.set("4A LLC")
            roles.set(
              listOf(
                "Software Developer",
                "Head of Development"
              )
            )
          }
        }
        organization {
          name = "4A LLC"
          url = "https://4ait.ru"
        }
        scm {
          connection = "scm:git:git://github.com:4ait/quarkus-hibernate-entity-mutator-lib.git"
          developerConnection = "scm:git:ssh://github.com:4ait/quarkus-hibernate-entity-mutator-lib.git"
          url = "https://github.com/4ait/quarkus-hibernate-entity-mutator-lib"
        }
      }
    }
  }
  repositories {
    maven {
      url =
        layout.buildDirectory
          .dir("staging-deploy")
          .get()
          .asFile
          .toURI()
    }
  }
}

repositories {
  mavenCentral()
}

allOpen {
  annotation("jakarta.persistence.Entity")
  annotation("jakarta.persistence.MappedSuperclass")
}

// Test entities are enhanced the way Quarkus enhances entities: lazy loading and dirty tracking,
// without Hibernate association management (the mutators manage associations).
hibernate {
  sourceSet.set("test")
  enhancement {
    enableLazyInitialization.set(true)
    enableDirtyTracking.set(true)
    enableAssociationManagement.set(false)
    enableExtendedEnhancement.set(false)
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
  dependsOn(tasks["jandex"])
}

val quarkusVersion = project.property("quarkusVersion") as String

dependencies {
  implementation("io.quarkus:quarkus-arc:${quarkusVersion}")
  implementation("io.quarkus:quarkus-hibernate-orm:${quarkusVersion}")
  implementation("io.quarkus:quarkus-core-deployment:${quarkusVersion}")

  implementation(kotlin("reflect"))
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

  // The Hibernate enhancer resolves entity supertypes through the testCompileClasspath configuration,
  // which, unlike the test source set classpath, does not contain the main classes.
  testImplementation(files(sourceSets.main.get().output.classesDirs))
  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
  testRuntimeOnly("com.h2database:h2:2.3.232")
}

tasks.named("compileTestKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
  compilerOptions {
    freeCompilerArgs.add("-Xdebug")
  }
}

jreleaser {
  project {
    copyright.set("4A LLC")
  }
  gitRootSearch.set(true)
  signing {
    active.set(Active.ALWAYS)
    armored.set(true)
  }
  release {
    github {
      overwrite.set(true)
      branch.set("master")
    }
  }
  deploy {
    maven {
      mavenCentral {
        create("maven-central") {
          active.set(Active.ALWAYS)
          url.set("https://central.sonatype.com/api/v1/publisher")
          stagingRepositories.add("build/staging-deploy")
          retryDelay.set(30)
        }
      }
    }
  }
}
