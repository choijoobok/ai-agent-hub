// @feat FEAT-040
dependencies {
    implementation(project(":backend:api"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.querydsl.jpa)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
