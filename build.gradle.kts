import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify

plugins {
    id("java-library")
    id("io.freefair.lombok") version "9.7.0"
    id("de.undercouch.download") version "5.7.0"
    kotlin("jvm") version "2.4.20" apply false
}

group = "io.ib67"
version = property("version")!!

allprojects {
    plugins.apply("java-library")
    plugins.apply("io.freefair.lombok")
    plugins.apply("org.jetbrains.kotlin.jvm")
    repositories {
        mavenCentral()
        maven("https://repo.opencollab.dev/main/") {
            name = "opencollab"
        }
        // DataFixerUpper 只发布在这个仓库，dfu-codecs 依赖它，而 dfu-codecs 经 datapack-loader 被全部模块依赖
        maven("https://libraries.minecraft.net") {
            name = "minecraft"
        }
        // kiwi 只发布在这个仓库，bot:api 的观察记录投递依赖它
        maven("https://repo.sfclub.cc/snapshots") {
            name = "kiwi"
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
        // 测试使用 Kotlin 编写
        testImplementation(kotlin("stdlib"))
        compileOnly("org.jetbrains:annotations:26.1.0")
    }

    tasks.test {
        useJUnitPlatform {
            excludeTags("isolated")
        }
    }

    // 带有 isolated 标签的测试类，每个类在单独的 JVM 中运行，见 story 模块的设计
    val isolatedTest = tasks.register<Test>("isolatedTest") {
        description = "运行带有 isolated 标签的测试类，每个类一个 JVM"
        group = "verification"
        val testSourceSet = project.the<SourceSetContainer>()["test"]
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        useJUnitPlatform {
            includeTags("isolated")
        }
        forkEvery = 1
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        // isolated 类的 ServerSetup 可以在创建服务端之前修改 Minestom 的 ServerProperties
        systemProperty("minestom.properties.mutable", "true")
        // 没有 isolated 测试的模块中，这个任务不发现任何测试
        failOnNoDiscoveredTests = false
        shouldRunAfter(tasks.test)
    }

    tasks.check {
        dependsOn(isolatedTest)
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
