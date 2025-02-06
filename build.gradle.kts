plugins {
    id("org.springframework.boot") version "2.7.3"
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.spring") version "1.7.10"
}

group = "com.test"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starter Web
    // Spring Boot Starter Web에서 Logback 제외
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "ch.qos.logback")
    }

    // Log4j 1.x 추가
    implementation("log4j:log4j:1.2.17")

    // 테스트 의존성
    // 테스트 의존성
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "ch.qos.logback")
    }
}
