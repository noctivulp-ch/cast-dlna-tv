pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cast-dlna-tv"
include(":app")
// Wulfenite se enganchará cuando toque la interfaz; primero se valida el
// receptor UPnP, que es lo que puede fallar contra controladores reales.
