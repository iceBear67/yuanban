import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify

plugins {
    id("java-library")
    id("io.freefair.lombok") version "9.7.0"
    id("de.undercouch.download") version "5.7.0"
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
        // DataFixerUpper 只发布在这个仓库，dfu-codecs 依赖它，而 dfu-codecs 经 datapack-loader 被全部模块依赖
        maven("https://libraries.minecraft.net") {
            name = "minecraft"
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

// 原版 26.2 服务端：多个模块的测试读取其中的数据，由根项目下载、校验并取出一次，全部模块使用同一份。
// 需要它的模块在自己的 build.gradle 中让 test 任务依赖 :extractVanillaServer，并读取 vanillaServerJar 给出的路径。
val vanillaBundle = layout.buildDirectory.file("vanilla/server-26.2-bundle.jar")
val vanillaServer = layout.buildDirectory.dir("vanilla/server")

val downloadVanillaServer by tasks.registering(Download::class) {
    src("https://piston-data.mojang.com/v1/objects/823e2250d24b3ddac457a60c92a6a941943fcd6a/server.jar")
    dest(vanillaBundle)
    overwrite(false)
}

val verifyVanillaServer by tasks.registering(Verify::class) {
    dependsOn(downloadVanillaServer)
    src(vanillaBundle)
    algorithm("SHA-1")
    checksum("823e2250d24b3ddac457a60c92a6a941943fcd6a")
}

tasks.register<Copy>("extractVanillaServer") {
    dependsOn(verifyVanillaServer)
    from(zipTree(vanillaBundle)) {
        include("META-INF/versions/26.2/server-26.2.jar")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(vanillaServer)
}

// 取出的服务端 jar 的绝对路径，供各模块传给测试
extra["vanillaServerJar"] = vanillaServer.get().file("server-26.2.jar").asFile.absolutePath
