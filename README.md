# CA AOS Guide

## Overview
This document is a guide for using the OpenDID authentication client, providing users with the functionality to create, store, and manage WalletToken, Lock/Unlock, Key, DID Document, and Verifiable Credential (VC) information required for OpenDID.


## S/W Specifications
| Category | Details |
|------|----------------------------|
| OS  | Android 14|
| Language  | Java 17|
| IDE  | Android Studio 4|
| Build System  | Gradle 8.2 |
| Compatibility | Android API level 34 or higher  |

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
5. Build and run the app
   - After selecting the target device, click the Run button at the top to build the app and run it on the selected device. If the build is successful, the app will run on the selected emulator or device.

## SDK Application Method
The Android SDK is referred to as `did-client-sdk-aos` below.
- *did-core-sdk-aos-1.0.0.jar*
- *did-utility-sdk-aos-1.0.0.jar*
- *did-datamodel-sdk-aos-1.0.0.jar*
- *did-wallet-sdk-aos-1.0.0.jar*
- *did-communication-sdk-aos-1.0.0.jar*

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
   - Open the build.gradle file for the app module, and add the following code to the dependencies section to include the `did-client-sdk-aos` library in the project:
    
    ```groovy
    dependencies {
        .
        .
        .
        implementation fileTree(dir: 'libs', include: ['*.jar'])
        
        implementation 'com.google.code.gson:gson:2.10.1'
        implementation 'androidx.biometric:biometric:1.1.0'
        implementation 'org.bitcoinj:bitcoinj-core:0.15.7'

        implementation 'com.madgag.spongycastle:core:1.54.0.0'
        implementation 'com.madgag.spongycastle:prov:1.54.0.0'
        implementation 'com.madgag.spongycastle:pkix:1.54.0.0'
        implementation 'com.madgag.spongycastle:pg:1.54.0.0'
        
        api "androidx.room:room-runtime:2.6.1"
        annotationProcessor "androidx.room:room-compiler:2.6.1"
    }
    ```

    b. Check the minSdkVersion and targetSdkVersion settings
   - In the android section of the build.gradle file, verify the minSdkVersion and targetSdkVersion settings and modify them to meet the project requirements.

    ```groovy
    android {
        compileSdkVersion 34

        defaultConfig {
            applicationId "org.omnione.did.ca"
            minSdkVersion 26
            targetSdkVersion 34
            versionCode 1
            versionName "1.0"
        }
    }
    ```

4. Import and Usage

    a. Modify the URLs Class
   - In the `Config.java` file of the project, update the URL information for each service provider:
    ```java
    public class Config {
        public static final String TAS_URL = "http://192.168.3.130:8090";
        public static final String VERIFIER_URL = "http://192.168.3.130:8092";
        public static final String CAS_URL = "http://192.168.3.130:8094";
        public static final String WALLET_URL = "http://192.168.3.130:8095";
        public static final String API_GATEWAY_URL = "http://192.168.3.130:8093";
        public static final String DEMO_URL = "http://192.168.3.130:8099";
    }
    ```

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

        - Correct Dependencies: Verify that the dependencies are correctly set in the `build.gradle` file.
        - SDK Version: Ensure that the project’s minSdkVersion and targetSdkVersion are compatible with the SDK being used.
        - Permissions: Check that the necessary permissions are correctly set in the AndroidManifest.xml file.

<br>

## Change Log

ChangeLog can be found : 
<br>
- [CA AOS](CHANGELOG.md)  

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for details on our code of conduct, and the process for submitting pull requests to us.


## License
[Apache 2.0](LICENSE)
