// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// Force patched versions of vulnerable transitive build-classpath dependencies.
// These come from AGP's bundled gRPC/Netty stack and are build-time only (not shipped in the APK).
buildscript {
    configurations.all {
        resolutionStrategy.eachDependency {
            val group = requested.group
            val name  = requested.name
            when {
                group == "io.netty" -> useVersion("4.1.129.Final")
                group == "com.google.protobuf" && name == "protobuf-kotlin" -> useVersion("3.25.5")
                group == "com.google.protobuf" && name == "protobuf-java"   -> useVersion("3.25.5")
                group == "org.bitbucket.b_c"   && name == "jose4j"          -> useVersion("0.9.6")
                group == "org.jdom"            && name == "jdom2"            -> useVersion("2.0.6.1")
                group == "org.apache.commons"  && name == "commons-compress" -> useVersion("1.26.0")
                group == "org.apache.commons"  && name == "commons-lang3"    -> useVersion("3.18.0")
            }
        }
    }
}
