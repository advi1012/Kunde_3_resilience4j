pluginManagement {
    repositories {
        maven("http://dl.bintray.com/kotlin/kotlin-eap")
        maven("http://repo.spring.io/libs-milestone")
        maven("http://repo.spring.io/plugins-release")

        mavenCentral()
        //jcenter()

        // https://plugins.gradle.org/m2
        gradlePluginPortal()

        // Snapshots von Spring Framework, Spring Data, Spring Security und Spring Cloud
        //maven("http://repo.spring.io/libs-snapshot")
    }
    
    //resolutionStrategy {
    //    eachPlugin {
    //        if (requested.id.id == "com.vanniktech.dependency.graph.generator") {
    //            useModule("guru.nidi:graphviz-java:0.7.0")
    //        }
    //    }
    //}
}
rootProject.name = "kunde"
