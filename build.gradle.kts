plugins {
    application
    id("org.graalvm.buildtools.native") version "1.1.14"
    id("edu.sc.seis.launch4j") version "4.0.0"
}

repositories {
    mavenCentral()
    google()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("ru.anseranser.jmkvpropedit.JMkvpropedit")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}



dependencies {
    implementation("commons-io:commons-io:2.22.0")
    implementation("org.ini4j:ini4j:0.5.4")
}

graalvmNative {
    binaries {
        named("main") {
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
            imageName.set("jmkvpropedit")
            useFatJar.set(true)
        }
    }
}

launch4j {
    mainClassName = "ru.anseranser.jmkvpropedit.JMkvpropedit"
    outfile = "jmkvpropedit.exe"
}
