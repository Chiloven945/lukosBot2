plugins {
    id("lukos.spring-boot-app")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":properties"))
    implementation(project(":core:model"))
    implementation(project(":core:runtime"))
    implementation(project(":command:framework"))
    implementation(project(":platform:api"))
    implementation(project(":infra:http"))

    implementation(project(":platform:telegram"))
    implementation(project(":platform:discord"))
    implementation(project(":platform:onebot"))

    implementation(project(":commands:basic"))
    implementation(project(":commands:admin"))
    implementation(project(":commands:integrations"))
    implementation(project(":commands:minecraft"))
    implementation(project(":commands:web"))
    implementation(project(":commands:media"))
    implementation(project(":commands:translate"))
    implementation(project(":commands:cli"))

    implementation(libs.shiro)
    implementation(libs.snakeyaml)
    implementation(libs.spring.boot.starter)
    runtimeOnly(libs.h2)
}

springBoot {
    mainClass.set("top.chiloven.lukosbot2.Main")
}
