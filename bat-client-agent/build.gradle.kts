import org.gradle.jvm.tasks.Jar
import java.io.File

plugins {
    java
}

group = "com.batclient"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
    testImplementation("org.ow2.asm:asm-util:9.7.1")
    testImplementation("org.ow2.asm:asm-commons:9.7.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(8)
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Premain-Class" to "com.batclient.cosmetics.AgentMain",
            "Agent-Class" to "com.batclient.cosmetics.AgentMain",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Implementation-Version" to project.version
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("bat-cosmetics")
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "module-info.class", "META-INF/versions/**/module-info.class")
    }
}

val relocateJar by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Rebuilds the agent fat jar with ASM relocated into batclient.shaded (avoids Fabric duplicate-ASM check)."
    dependsOn("jar", "testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.batclient.cosmetics.tools.Relocator")
    doFirst {
        val input = tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
        val output = File(layout.buildDirectory.dir("libs").get().asFile, "bat-cosmetics.jar").absolutePath
        setArgs(listOf(input, output))
    }
}

val ownershipChecks = listOf("early", "late", "external", "external-low", "standalone").map { scenario ->
    tasks.register<JavaExec>("verifyPerformanceOwnership" + scenario.replaceFirstChar { it.uppercase() }) {
        dependsOn("testClasses")
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("com.batclient.cosmetics.PerformanceOwnershipValidation")
        args(scenario)
    }
}
tasks.named("check") { dependsOn(ownershipChecks) }
val asyncCapeCheck = tasks.register<JavaExec>("verifyAsyncCapeFrames") {
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.batclient.cosmetics.CapeFrameLoaderValidation")
}
tasks.named("check") { dependsOn(asyncCapeCheck) }
// Agent validations are executable mains (ASM fixtures), not JUnit test classes.
tasks.named<Test>("test") { failOnNoDiscoveredTests = false }
