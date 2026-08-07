# CA AOS Guide

## Overview
This document is a guide for using the OpenDID authentication client (Certified App), providing users with the functionality to create, store, and manage WalletToken, Lock/Unlock, Key, DID Document, and Verifiable Credential (VC) information required for OpenDID.

This is the 3.0 base application. In addition to the native OpenDID protocol, it supports credential issuance and presentation over **OID4VC** (OID4VCI / OID4VP) and handles the **DID-based SD-JWT (`sd-jwt-did`)** credential format.

### Key Features
| Feature | Details |
| ------- | ------- |
| User registration | Wallet creation, DID Document registration, user identity verification |
| Authentication | PIN and biometric (fingerprint / face) authentication |
| Issuance | Native OpenDID protocol, OID4VCI (pre-authorized code, user-initiated via WebView) |
| Presentation | Native OpenDID protocol, OID4VP, ZKP-based presentation |
| Credential format | OpenDID VC, DID-based SD-JWT (`sd-jwt-did`) |
| Selective disclosure | Claim-level selection when submitting a presentation |
| Credential status | Credential status verification via Token Status List |
| Credential management | Credential list / detail view, deletion and revocation |


## S/W Specifications
| Category         | Details                                                 |
| ---------------- | ------------------------------------------------------- |
| OS               | Android 16 (API Level 36)                               |
| Language         | Java 21                                                 |
| IDE              | Android Studio 2025.3 or later                          |
| Build System     | Gradle 9.4.1 / Android Gradle Plugin 9.1.0              |
| Architecture     | MVVM (Activity + ViewModel + LiveData + Repository), Hilt |
| UI               | View + XML with ViewBinding / DataBinding (Material 3)  |
| Compatibility    | compileSdk 36, targetSdk 36, minSdk 26                  |
| Test Environment | Minimum Requirements: Android 8.0 (Oreo, API Level 26)  |
|                  | Recommended Requirements: Android 16 (API Level 36)     |

## DIDCA Project Clone and Checkout
```git
git clone https://github.com/OmniOneID/did-ca-aos.git
```

## Build Method
: This is how to compile and test the app using Android Studio.
1. Install Android Studio
   - After installing Android Studio, run it. From the top menu, select File > Open to open the desired project folder. 
     (source/did-ca-aos)
2. Open the project
   - Once the project is open, you can check the source files, resource files, and configuration files in the left-side Project window of the Android Studio window. Here you can manage all the code and resources of the app.
3. Select emulator or real device
   - In the Android Studio window's top bar, you can select the target device to build and run the app in the Device Manager or Target Device menu.
   - Emulator: You can run the app on a virtual Android device. To create a new virtual device, use the AVD Manager to set up a new emulator. There may be differences from a real device, and some features may not work on the emulator.
   - Device: If a real Android device is connected via USB, select that device to run the app. USB debugging must be enabled in the developer options.
4. Check project settings
   - Before building, you need to verify the project settings.
   - Go to File > Project Structure and in the Modules section, verify the Compile SDK Version, Build Tools Version, Min SDK Version, etc., and modify them if necessary.
   - In the build.gradle file, check the dependencies, defaultConfig, and buildTypes settings to manage the app's build configuration.
   - Dependency coordinates are declared in the Gradle version catalog (`gradle/libs.versions.toml`) and referenced as `libs.*` from `app/build.gradle`.
5. Build and run the app
   - After selecting the target device, click the Run button at the top to build the app and run it on the selected device. If the build is successful, the app will run on the selected emulator or device.

## SDK Application Method
The Android SDK is referred to as `did-client-sdk-aos` below.
- *did-wallet-sdk-aos-3.0.0-0807.jar*
- *did-sd-jwt-vc-sdk-server-2.5.0.jar*

Please refer to the respective links for their own licenses for third-party libraries used by each SDK.
<br>
[Client SDK License-dependencies](https://github.com/OmniOneID/did-client-sdk-aos/blob/main/dependencies-license.md)

<br>

### How to apply the did-client-sdk-aos library to the DIDCA project in Android Studio:
1. Prepare the did-client-sdk-aos library

- If you do not have the above library files, you need to build them from the SDK repository to generate the jar files.
[Move to Client SDK](https://github.com/OmniOneID/did-client-sdk-aos)

2. Add the did-client-sdk-aos library

    a. Add jar files to the project
   - Open the project in Android Studio.
   - Create a `libs` folder under the `app` directory in the Project window. (Skip this step if the folder already exists.)
   - Copy the prepared jar files into the `libs` folder.

    b. Add jar files to the project dependencies
     - After copying the jar files to the `libs` folder, right-click each jar file in the Project window and select "Add as Library..."
     - Android Studio will automatically add the jar files as project dependencies.

3. Modify the build.gradle file

    a. Modify the dependencies section
   - Open the build.gradle file for the app module. The jar files under `libs` are picked up by `fileTree`, and the remaining dependencies are referenced from the version catalog:

    ```groovy
    dependencies {
        implementation fileTree(dir: 'libs', include: ['*.jar'])

        implementation libs.gson
        implementation libs.biometric
        implementation libs.bitcoinj.core

        implementation libs.spongycastle.core
        implementation libs.spongycastle.prov
        implementation libs.spongycastle.prix
        implementation libs.spongycastle.pg

        api libs.room.runtime
        annotationProcessor libs.room.compiler

        implementation libs.hilt.android
        annotationProcessor libs.hilt.compiler

        implementation libs.bundles.jackson
        implementation libs.nimbus.jose.jwt
    }
    ```

   - The actual versions are declared in `gradle/libs.versions.toml`. Add a new dependency there first, then reference it as `libs.<alias>`.

    b. Check the minSdk and targetSdk settings
   - In the android section of the build.gradle file, verify the minSdk and targetSdk settings and modify them to meet the project requirements.

    ```groovy
    android {
        namespace 'org.omnione.did.ca'
        compileSdk {
            version = release(36) {
                minorApiLevel = 1
            }
        }

        defaultConfig {
            applicationId "org.omnione.did.ca"
            minSdk 26
            targetSdk 36
            versionCode 30000
            versionName "3.0.0"
        }

        compileOptions {
            sourceCompatibility JavaVersion.VERSION_21
            targetCompatibility JavaVersion.VERSION_21
        }
    }
    ```

4. Import and Usage

    a. Modify the service provider URLs
   - Service provider URLs are declared as `buildConfigField` entries in `app/build.gradle`. Change the `url` value to point at your environment:
    ```groovy
    defaultConfig {
        def url = "http://192.168.3.110"
        buildConfigField "String", "TAS_URL", "\"${url}:8090\""
        buildConfigField "String", "CAS_URL", "\"${url}:8094\""
        buildConfigField "String", "API_GW_URL", "\"${url}:8093\""
        buildConfigField "String", "WALLET_URL", "\"${url}:8095\""
        buildConfigField "String", "VERIFIER_URL", "\"${url}:8092\""
        buildConfigField "String", "DEMO_URL", "\"${url}:8099\""
    }
    ```
   - At runtime these values are provided to the app through the `AppConfig` record (`data/config/AppConfig.java`) via Hilt injection.

    b. Using the did-client-sdk-aos Module
   - At the top of the Java/Kotlin file where you want to use the class or method, import as follows:
    ```java
    import  org.omnione.did.sdk.core;
    import  org.omnione.did.sdk.utility;
    import  org.omnione.did.sdk.datamodel;
    import  org.omnione.did.sdk.wallet;
    import  org.omnione.did.sdk.communication;
    ```

   - You can now use the features provided by the did-client-sdk-aos module in your source code:
    ```java
    try {
        String hWalletToken = WalletAPI.createWalletToken(WWALLET_TOKEN_PURPOSE.LIST_VC);

        List<Credential> credentials = WalletAPI.getAllCredentials(hWalletToken);

        if (credentials != null) {
            for (Credential vc : credentials) {
                Log.d("VC", vc.toJson());
            }
        }

    } catch (WalletException e) {
        Log.e("WalletException", "Message: " + e.getMessage());
    } catch (CommunicationException e) {
        Log.e("CommunicationException", "Message: " + e.getMessage());
    } catch (UtilityException e) {
        Log.e("UtilityException", "Message: " + e.getMessage());
    }
    ```

5. Build and Test

    a. Build and Run
   - Click the Run button at the top of Android Studio to build and run the project. If any errors occur during the build process, check the Build window for error details and resolve the issues.

    b. Test
   - Once the build is successfully completed, run the app to ensure that the did-client-sdk-aos library functions correctly. Use Android Studio’s debugger and logs to identify any potential issues.

6. Troubleshooting
   - If the did-client-sdk-aos library is not properly loaded or functioning, check the following:

        - Correct Dependencies: Verify that the dependencies are correctly set in the `build.gradle` file and in `gradle/libs.versions.toml`.
        - SDK Version: Ensure that the project’s minSdk and targetSdk are compatible with the SDK being used.
        - Permissions: Check that the necessary permissions are correctly set in the AndroidManifest.xml file.

<br>

## Project Structure
```
source/did-ca-aos/
└── app/src/main/java/org/omnione/did/ca/
    ├── config/          Constants
    ├── data/
    │   ├── config/      AppConfig
    │   ├── datasource/  Local persistence
    │   ├── model/       Domain models
    │   ├── network/     HTTP client and protocol operations
    │   ├── repository/  Domain repositories
    │   ├── sdk/         did-client-sdk-aos gateway
    │   └── statuslist/  Token Status List handling
    ├── di/              Hilt modules
    ├── protocol/        OID4VCIProtocol, OID4VPProtocol
    ├── ui/<feature>/    One package per screen (Activity + ViewModel + UiState)
    └── util/            Utilities
```

Each screen is implemented as an Activity with its own ViewModel and UiState under `ui/<feature>/`.

## Change Log

ChangeLog can be found : 
<br>
- [CA AOS](CHANGELOG.md)  

## OpenDID Demonstration Videos <br>
To watch our demonstration videos of the OpenDID system in action, please visit our [Demo Repository](https://github.com/OmniOneID/did-demo-server). <br>

These videos showcase key features including user registration, VC issuance, and VP submission processes.

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for details on our code of conduct, and the process for submitting pull requests to us.


## License
[Apache 2.0](LICENSE)
