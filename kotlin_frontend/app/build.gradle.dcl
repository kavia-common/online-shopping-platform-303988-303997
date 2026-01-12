androidApplication {
    namespace = "org.example.app"

    dependencies {
        implementation("org.apache.commons:commons-text:1.11.0")
        implementation(project(":utilities"))

        // UI + Material
        implementation("androidx.appcompat:appcompat:1.7.0")
        implementation("androidx.constraintlayout:constraintlayout:2.2.0")
        implementation("com.google.android.material:material:1.12.0")

        // Single-activity navigation (Fragments)
        implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
        implementation("androidx.navigation:navigation-ui-ktx:2.8.5")

        // Observable state for cart (LiveData)
        implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
        implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    }
}
