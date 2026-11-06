plugins {
    kotlin("jvm") version "2.1.21"
    `maven-publish`
}

group = "com.sunrun.security"
version = "1.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("software.amazon.awssdk:sns:2.31.50")
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
