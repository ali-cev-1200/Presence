plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.ali_1200.presence"
    compileSdk = 35

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.ali_1200.presence"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    // کتابخانه Room برای مدیریت پایگاه داده SQLite در اندروید
    implementation("androidx.room:room-runtime:2.6.1")
    // برای کامپایل کدهای مرتبط با Room
    kapt("androidx.room:room-compiler:2.6.1")

    // برای پشتیبانی از Coroutines در عملیات پایگاه داده Room
    implementation("androidx.room:room-ktx:2.5.0")

    // برای مدیریت چرخه حیات ViewModel در معماری MVVM
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    // برای استفاده از LiveData برای مدیریت داده‌های پویا و قابل مشاهده
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    // برای مدیریت چرخه حیات در بخش‌های مختلف اپلیکیشن
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

//    // کتابخانه برای استفاده از تاریخ شمسی و تبدیل آن
//    implementation("com.github.samanzamani:PersianDate:1.7.1")

    // کتابخانه متریال دیزاین گوگل برای استفاده از کامپوننت‌ها و استایل‌های متریال
    implementation("com.google.android.material:material:1.12.0")
}