// Top-level build file where you can add configuration options common to all
// sub-projects/modules. Plugins are declared here with `apply false` so that
// each module can apply only what it needs.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}
