plugins {
    application
}

dependencies {
    implementation(project(":javaconst"))
    compileOnly(project(path = ":javaconst", configuration = "javacPlugin"))
}

application {
    mainClass = "com.hirshi001.example.Main"
}

tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
    options.forkOptions.jvmArgs = listOf("--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
    options.compilerArgs.add("-Xplugin:ConstPlugin")
}
