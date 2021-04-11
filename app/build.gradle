apply plugin: 'com.android.application'

android {
    compileSdkVersion 29
    buildToolsVersion "29.0.3"

    defaultConfig {
        applicationId "com.hippo.unifile.example"
        minSdkVersion 21
        targetSdkVersion 29
        versionCode 8
        versionName '1.0.0'
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    implementation project(':library')

    compileOnly 'androidx.annotation:annotation:1.2.0'
}
