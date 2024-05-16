val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "1.9.24"
    id("io.ktor.plugin") version "2.3.11"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("com.example.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    implementation("dev.langchain4j:langchain4j:0.30.0")
    implementation("dev.langchain4j:langchain4j-core:0.30.0")
    implementation("dev.langchain4j:langchain4j-open-ai:0.30.0")
    implementation("dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:0.30.0")
    implementation("dev.langchain4j:langchain4j-neo4j:0.30.0")
}
