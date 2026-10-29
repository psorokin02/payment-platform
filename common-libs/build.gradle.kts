plugins {
    `java-library`
}

description = "Common Libs — общие DTO, события и типы сервисов"

dependencies {
    // Версии совпадают с BOM Spring Boot трёх приложений.
    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    compileOnly("ch.qos.logback:logback-classic")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    testImplementation("ch.qos.logback:logback-classic")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
