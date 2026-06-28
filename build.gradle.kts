tasks.register<JavaExec>("runReflection") {
    group = "application"
    mainClass.set("net.drachi.cdbe.battle.attack.TestPartySync")
    classpath = sourceSets["main"].runtimeClasspath
}
