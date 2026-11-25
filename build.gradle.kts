plugins {
    kotlin("jvm") version "2.4.21"
    `maven-publish`
}

group = "com.sunrun.security"
version = "1.0.6"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        // Ship Java 17 bytecode so Quarkus 2.x / Mandrel 22.3 (Java 17) native builds can consume this jar.
        // Nothing in this library needs Java 21 language or API features.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("software.amazon.awssdk:sns:2.31.50") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.11")
    implementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/sunrun/sr-sec-kt-sns-logger")
            credentials {
                username =
                    System.getenv("GITHUB_ACTOR")
                        ?: System.getenv("USER_NAME_GITHUB_ACTIONS_PACKAGES")
                password =
                    System.getenv("GITHUB_TOKEN")
                        ?: System.getenv("USER_TOKEN_GITHUB_ACTIONS_PACKAGES")
            }
        }
    }
}
