pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.dcm4che.org/maven2/")
        // Required to resolve dcm4che parent BOM (weasis-core-img-bom)
        maven(url = "https://raw.githubusercontent.com/nroduit/mvn-repo/master/")
        maven(url = "https://repository.jboss.org/nexus/content/groups/public/")
    }
}

rootProject.name = "dicomcamera"
include(":app")
include(":dicom")
include(":identity")
