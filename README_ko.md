# CA AOS Guide

## 개요
본 문서는 OpenDID 인증 클라이언트를 사용하기 위한 가이드이며, 사용자에게 OpenDID에 필요한 WalletToken, Lock/Unlock, Key, DID Document(DID 문서), Verifiable Credential(이하 VC) 정보를 생성, 저장, 관리하는 기능을 제공합니다.


## S/W 사양
| 구분             | 내용                                                     |
| ---------------- | ------------------------------------------------------ |
| OS               | Android 14                                             |
| Language         | Java 20                                                |
| IDE              | Android Studio 4                                       |
| Build System     | Gradle 8.2                                             |
| Compatibility    | Android API level 34 or higher                         |
| Test Environment | Minimum Requirements: Android 8.0 (Oreo, API Level 26) |
|                  | Recommended Requirements: Android 14 (API Level 34)    |


## DIDCA 프로젝트 클론 및 체크아웃
```git
git clone https://github.com/OmniOneID/did-ca-aos.git
```

## 빌드 방법
: Android Studio를 사용하여 앱을 컴파일하고 테스트하는 방법입니다.
1. Android Studio 설치
   - Android Studio를 설치한 후, 실행합니다. 상단 메뉴에서 File > Open을 선택하여 원하는 프로젝트 폴더를 엽니다. 
     (source/did-ca-aos)
2. 프로젝트 열기
   - 프로젝트가 열리면, Android Studio 창의 좌측 Project 창에서 소스 파일, 리소스 파일 및 설정 파일을 확인할 수 있습니다. 여기에서 앱의 모든 코드와 리소스를 관리할 수 있습니다.
3. 에뮬레이터 또는 실제 기기 선택
   - Android Studio 창 상단의 Device Manager 또는 Target Device 메뉴에서 앱을 빌드하고 실행할 타겟 기기를 선택할 수 있습니다.
   - Emulator: 가상의 Android 기기에서 앱을 실행할 수 있습니다. 새로운 가상 기기를 생성하려면 AVD Manager를 사용하여 새로운 에뮬레이터를 설정할 수 있습니다. 에뮬레이터에서는 실제 기기와 차이가 있어 일부 기능이 동작하지 않을 수 있습니다.
   - Device: 실제 Android 기기를 USB로 연결한 경우 해당 기기를 선택하여 앱을 실행할 수 있습니다. 개발자 옵션에서 USB 디버깅이 활성화되어 있어야 합니다.
4. 프로젝트 설정 확인
   - 빌드하기 전에 프로젝트 설정을 확인해야 합니다.
   - File > Project Structure로 이동하여 Modules 섹션에서 Compile SDK Version, Build Tools Version, Min SDK Version 등 빌드 설정을 확인하고 필요 시 수정합니다.
   - build.gradle 파일에서 dependencies, defaultConfig 및 buildTypes 설정을 확인하여 앱의 빌드 구성을 관리할 수 있습니다.
5. 앱 빌드 및 실행
   - 타겟 기기를 선택한 후, 상단의 Run 버튼을 클릭하여 앱을 빌드하고 선택한 기기에서 실행할 수 있습니다. 빌드가 성공하면 앱이 선택된 에뮬레이터 또는 기기에서 실행됩니다.

## SDK 적용 방법
아래 Android SDK를 did-client-sdk-aos로 지칭합니다.
- *did-core-sdk-aos-1.0.0.jar*
- *did-utility-sdk-aos-1.0.0.jar*
- *did-datamodel-sdk-aos-1.0.0.jar*
- *did-wallet-sdk-aos-1.0.0.jar*
- *did-communication-sdk-aos-1.0.0.jar*

각 SDK가 사용하는 타사 라이브러리에 대한 자체 라이선스는 해당 링크를 참고해주세요. <br>
[Client SDK License-dependencies](https://github.com/OmniOneID/did-client-sdk-aos/blob/main/dependencies-license.md)

<br>

Android Studio에서 did-client-sdk-aos 라이브러리를 DIDCA 프로젝트에 적용하는 방법
1. did-client-sdk-aos 라이브러리 준비

- 만약 위의 라이브러리 파일이 없는 경우, 각 SDK의 레포지토리에서 빌드하여 jar 파일들을 생성해야 합니다.
[Client SDK로 이동](https://github.com/OmniOneID/did-client-sdk-aos)

2. did-client-sdk-aos 라이브러리 추가

    a. 프로젝트에 jar 파일 추가하기
   - Android Studio에서 프로젝트를 엽니다.
   - Project 창의 app 디렉토리에서 libs 폴더를 생성합니다. (libs 폴더가 이미 존재하는 경우 이 단계는 생략합니다.)
   - 위에서 준비한 jar 파일들을 libs 폴더에 복사합니다.

    b. jar 파일을 프로젝트에 추가하기
     - libs 폴더에 jar 파일들을 복사한 후, Project 창에서 각 jar 파일을 우클릭하고 Add as Library...를 선택합니다.
     - Android Studio가 jar 파일들을 자동으로 프로젝트의 종속성(dependencies)으로 추가합니다.

3. build.gradle 파일 수정

    a. dependencies 섹션 수정
   - app 모듈의 build.gradle 파일을 열고, dependencies 섹션에 다음 코드를 추가하여`did-client-sdk-aos 라이브러리를 프로젝트에 포함시킵니다:
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

    b. minSdkVersion 및 targetSdkVersion 설정 확인
   - build.gradle 파일의 android 섹션에서 minSdkVersion 및 targetSdkVersion 설정을 확인하고, 프로젝트 요구사항에 맞게 수정합니다.

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

4. Import 및 사용

    a. URLs 클래스 수정
   - 프로젝트의 `Config.java` 파일에서 각 사업자의 URL 정보를 수정합니다:
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

    b. did-client-sdk-aos 모듈 사용
   - 사용할 클래스나 메서드가 있는 Java/Kotlin 파일의 최상단에 다음과 같이 임포트합니다:
    ```java
    import  org.omnione.did.sdk.core;
    import  org.omnione.did.sdk.utility;
    import  org.omnione.did.sdk.datamodel;
    import  org.omnione.did.sdk.wallet;
    import  org.omnione.did.sdk.communication;
    ```

   - 이제 did-client-sdk-aos 모듈에서 제공하는 기능을 소스 코드에서 사용할 수 있습니다:
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

5. 빌드 및 테스트

    a. 빌드 및 실행
   - Android Studio 상단의 Run 버튼을 눌러 프로젝트를 빌드하고 실행합니다. 만약 빌드 중 에러가 발생하면, Build 창에서 에러 내용을 확인하고 문제를 해결합니다.

    b. 테스트
   - 빌드가 성공적으로 완료되면, 앱을 실행하여 did-client-sdk-aos 라이브러리의 기능이 제대로 동작하는지 확인합니다. Android Studio의 디버거와 로그를 활용해 문제가 발생했는지 여부를 파악할 수 있습니다.

6. 문제 해결
   - 만약 did-client-sdk-aos 라이브러리가 제대로 로드되지 않거나 작동하지 않는 경우, 다음 사항들을 확인해보세요:

        - Correct Dependencies: build.gradle` 파일에서 종속성이 정확하게 설정되었는지 확인합니다.
        - SDK Version: 프로젝트의 minSdkVersion 및 targetSdkVersion이 사용 중인 SDK와 호환되는지 확인합니다.
        - Permission: AndroidManifest.xml에서 필요한 권한이 제대로 설정되어 있는지 확인합니다.

<br>

## 수정내역

ChangeLog는 아래에서 확인할 수 있습니다.
<br>
- [CA AOS](CHANGELOG.md)   

## 데모 영상 <br>
OpenDID 시스템의 실제 동작을 보여주는 데모 영상은 [Demo Repository](https://github.com/OmniOneID/did-demo-server)에서 확인하실 수 있습니다. <br>
사용자 등록, VC 발급, VP 제출 등 주요 기능들을 영상으로 확인하실 수 있습니다.

## 기여

Contributing 및 pull request 제출 절차에 대한 자세한 내용은 [CONTRIBUTING.md](CONTRIBUTING.md)와 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) 를 참조하세요.

## 라이선스
[Apache 2.0](LICENSE)
