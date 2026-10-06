plugins {
    id("java-library")
    id("io.freefair.lombok") version "9.7.0"
}

group = "io.ib67"
version = property("version")!!

allprojects {
    plugins.apply("java-library")
    plugins.apply("io.freefair.lombok")
    repositories {
        mavenCentral()
        maven("https://repo.opencollab.dev/main/") {
            name = "opencollab"
        }
        mavenLocal()
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:6.0.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        compileOnly("org.jetbrains:annotations:26.1.0")
    }

    tasks.test {
        useJUnitPlatform()
    }
}