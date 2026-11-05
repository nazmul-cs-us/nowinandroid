plugins {
    alias(libs.plugins.nowinandroid.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.starception.submission.core.family" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
