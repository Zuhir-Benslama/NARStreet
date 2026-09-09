// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Pin patched versions of transitive build-time dependencies (AGP bundletool/jetifier/sdklib
// transitives) to close dependabot advisories until upstream moves off the vulnerable versions.
buildscript {
    configurations.all {
        resolutionStrategy {
            force(
                "org.bitbucket.b_c:jose4j:0.9.6",                  // DoS via compressed JWE
                "org.jdom:jdom2:2.0.6.1",                          // XXE injection
                "org.apache.commons:commons-lang3:3.18.0",         // uncontrolled recursion
                "org.apache.httpcomponents:httpclient:4.5.14",     // XSS in HttpClient
                "org.bouncycastle:bcprov-jdk18on:1.84",            // weak crypto / LDAP injection
                "org.bouncycastle:bcpkix-jdk18on:1.84",
            )
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply true
}

spotless {
    kotlin {
        target(
            project.fileTree(mapOf("dir" to "app", "include" to listOf("src/**/*.kt"))),
        )
        ktlint().editorConfigOverride(mapOf(
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            "max_line_length" to "120",
        ))
    }
}


