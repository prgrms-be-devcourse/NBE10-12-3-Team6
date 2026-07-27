plugins {
    java
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "csh"
version = "0.0.1-SNAPSHOT"
description = "back"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    //Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")

    //JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // QueryDSL JPA
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")

    // H2 Console
    implementation("org.springframework.boot:spring-boot-h2console")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    //Prometheus
    // 애플리케이션의 각종 상태(메모리, 스레드 등)를 측정하는 도구
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 측정된 데이터를 프로메테우스가 읽을 수 있는 형태로 변환해 주는 도구
    implementation("io.micrometer:micrometer-registry-prometheus")

    // AWS S3 연동을 위한 의존성
    implementation("org.springframework.cloud:spring-cloud-starter-aws:2.2.6.RELEASE")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")



    // QFile 생성 및 가져오기
    annotationProcessor("com.querydsl:querydsl-apt:5.1.0:jakarta")
    annotationProcessor("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    //스프링 배치
    implementation ("org.springframework.boot:spring-boot-starter-batch-jdbc")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Copy>("installGitHooks") {
    from("${rootDir}/hooks")
    into("${rootDir}/../.git/hooks")
    filePermissions {unix("755")}
}

tasks.named("build") {
    dependsOn("installGitHooks") }