plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "1.9.21"
    application
}

tasks.register<Exec>("bundleOpenAPI") {
    dependsOn("installRedocly")
    workingDir = file(".")

    commandLine("npx", "@redocly/cli", "bundle",
        "src/main/resources/openapi/api-docs.yaml",
        "--output", "src/main/resources/openapi/bundled-api-docs.yaml",
        "--ext", "yaml",
        "--dereferenced"
    )

    doFirst {
        println("Bundling OpenAPI with Redocly CLI...")
    }

    doLast {
        println("OpenAPI bundled successfully!")
    }
}

tasks.register<Exec>("installRedocly") {
    workingDir = file(".")
    commandLine("npm", "install", "-D", "@redocly/cli")

    onlyIf {
        !file("node_modules/@redocly/cli").exists()
    }
}

// Update processResources to depend on bundling
tasks.named("processResources") {
    dependsOn("bundleOpenAPI")
}

repositories { mavenCentral() }

dependencies {
    val ktor = "3.0.0"
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-websockets:$ktor")
    implementation("io.ktor:ktor-client-cio:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Exposed ORM for database
    implementation("org.jetbrains.exposed:exposed-core:0.45.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.45.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.45.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.45.0")
    implementation("org.jetbrains.exposed:exposed-json:0.45.0")

    // Postgres stack
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:11.14.1")              // or keep your 10.x, but match versions
    runtimeOnly("org.flywaydb:flyway-database-postgresql:11.14.1")  // <-- REQUIRED since v10
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.jooq:jooq:3.20.7")

    // Need for JS transformations
    implementation("org.graalvm.polyglot:polyglot:23.1.1")
    implementation("org.graalvm.polyglot:js:23.1.1")

    // For password hashing
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.github.f4b6a3:ulid-creator:5.0.0")

    // Metrics (optional)
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktor")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.5")

    // Kafka client
    implementation("org.apache.kafka:kafka-clients:3.5.1")

    // Documentation generation
    implementation("io.ktor:ktor-server-openapi:${ktor}")
    implementation("io.ktor:ktor-server-swagger:${ktor}")

    testImplementation(kotlin("test"))
}

application { mainClass.set("co.vendistax.splatcast.App.kt") }
tasks.test { useJUnitPlatform() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
