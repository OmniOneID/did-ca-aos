# CA AOS Guide

## 개요
본 문서는 OpenDID 인증 클라이언트(Certified App)를 사용하기 위한 가이드이며, 사용자에게 OpenDID에 필요한 WalletToken, Lock/Unlock, Key, DID Document(DID 문서), Verifiable Credential(이하 VC) 정보를 생성, 저장, 관리하는 기능을 제공합니다.

v3.0.0 앱으로, 기존 OpenDID 자체 프로토콜과 함께 **DID 기반 OID4VC(SD-JWT VC, mdoc)**(OID4VCI / OID4VP) 발급·제시와 mdoc 근접 제시를 지원합니다.

### 주요 기능
| 기능 | 내용 |
| ---- | ---- |
| 사용자 등록 | 지갑 생성, DID Document 등록, 사용자 신원 확인 |
| 인증 | PIN 및 생체(지문 / 얼굴) 인증 |
| 발급 | OpenDID 자체 프로토콜, OID4VCI (pre-authorized code, 웹뷰 기반 User-Init) |
| 제시 | OpenDID 자체 프로토콜, OID4VP, ZKP 기반 제시 |
| 근접 제시 | QR 또는 NFC device engagement 후 BLE / Wi-Fi Aware 로 mdoc 제시 |
| 크리덴셜 포맷 | OpenDID VC, DID 기반 SD-JWT VC(`dc+sd-jwt-did`), DID 기반 mdoc(`mso_mdoc-did`) |
| 선택적 제출 | 제시 시 claim 단위 선택 |
| 크리덴셜 상태 확인 | Token Status List 기반 상태 검증 (SD-JWT VC, mdoc) |
| 크리덴셜 관리 | 목록 / 상세 조회, 삭제 및 폐기 |


## S/W 사양
| 구분             | 내용                                                     |
| ---------------- | ------------------------------------------------------- |
| OS               | Android 16 (API Level 36)                               |
| Language         | Java 21                                                 |
| IDE              | Android Studio 2025.3 이상                               |
| Build System     | Gradle 9.4.1 / Android Gradle Plugin 9.1.0              |
| Architecture     | MVVM (Activity + ViewModel + LiveData + Repository), Hilt |
| UI               | View + XML, ViewBinding / DataBinding (Material 3)      |
| Compatibility    | compileSdk 36, targetSdk 36, minSdk 26                  |
| Test Environment | Minimum Requirements: Android 8.0 (Oreo, API Level 26)  |
|                  | Recommended Requirements: Android 16 (API Level 36)     |


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
   - 의존성 좌표는 Gradle 버전 카탈로그(`gradle/libs.versions.toml`)에 정의하고 `app/build.gradle`에서 `libs.*`로 참조합니다.
5. 앱 빌드 및 실행
   - 타겟 기기를 선택한 후, 상단의 Run 버튼을 클릭하여 앱을 빌드하고 선택한 기기에서 실행할 수 있습니다. 빌드가 성공하면 앱이 선택된 에뮬레이터 또는 기기에서 실행됩니다.

## SDK 적용 방법
아래 Android SDK를 did-client-sdk-aos로 지칭합니다.
- *did-wallet-sdk-aos-3.0.0.jar*
- *proximity-holder-1.0.0.aar* (`did-proximity-sdk-android` 빌드 산출물)

각 SDK가 사용하는 타사 라이브러리에 대한 자체 라이선스는 해당 링크를 참고해주세요. <br>
[Client SDK License-dependencies](https://github.com/OmniOneID/did-client-sdk-aos/blob/main/dependencies-license.md)
<br>
[Proximity SDK License-dependencies](https://github.com/OmniOneID/did-proximity-sdk-android/blob/main/dependencies-license.md)

<br>

Android Studio에서 did-client-sdk-aos 라이브러리를 DIDCA 프로젝트에 적용하는 방법
1. did-client-sdk-aos 라이브러리 준비

- 만약 위의 라이브러리 파일이 없는 경우, 각 SDK의 레포지토리에서 빌드하여 jar · aar 파일들을 생성해야 합니다.
[Client SDK로 이동](https://github.com/OmniOneID/did-client-sdk-aos)
<br>
[Proximity SDK로 이동](https://github.com/OmniOneID/did-proximity-sdk-android)

2. did-client-sdk-aos 라이브러리 추가

    a. 프로젝트에 jar · aar 파일 추가하기
   - Android Studio에서 프로젝트를 엽니다.
   - Project 창의 app 디렉토리에서 libs 폴더를 생성합니다. (libs 폴더가 이미 존재하는 경우 이 단계는 생략합니다.)
   - 위에서 준비한 jar · aar 파일들을 libs 폴더에 복사합니다.

    b. jar 파일을 프로젝트에 추가하기
     - libs 폴더에 jar 파일들을 복사한 후, Project 창에서 각 jar 파일을 우클릭하고 Add as Library...를 선택합니다.
     - Android Studio가 jar 파일들을 자동으로 프로젝트의 종속성(dependencies)으로 추가합니다.
     - aar 파일은 이 방법으로 추가하지 않고, build.gradle 에서 `files(...)`로 선언합니다 (3단계 참고).

3. build.gradle 파일 수정

    a. dependencies 섹션 수정
   - app 모듈의 build.gradle 파일을 엽니다. `libs` 폴더의 jar 파일은 `fileTree`로 포함되고, 근접 aar 은 `files(...)`로 직접 참조하며, 나머지 의존성은 버전 카탈로그에서 참조합니다:
    ```groovy
    dependencies {
        implementation fileTree(dir: 'libs', include: ['*.jar'])

        implementation libs.appcompat
        implementation libs.material
        implementation libs.core.splashscreen
        implementation libs.lifecycle.viewmodel
        implementation libs.lifecycle.livedata
        implementation libs.viewpager2
        implementation libs.gridlayout
        implementation libs.activity
        implementation libs.fragment
        implementation libs.recyclerview
        implementation libs.constraintlayout

        implementation libs.gson
        implementation libs.zxing.android.embedded
        implementation libs.bitcoinj.core
        implementation libs.biometric

        api libs.room.runtime

        implementation libs.spongycastle.core
        implementation libs.spongycastle.prov

        implementation libs.hilt.android
        annotationProcessor libs.hilt.compiler

        implementation libs.nimbus.jose.jwt

        implementation libs.upokecenter.cbor

        implementation files('libs/proximity-holder-1.0.0.aar')
    }
    ```

   - aar 에는 POM 이 없으므로, aar 이 의존하는 `spongycastle` core/prov 와 `upokecenter.cbor` 는 위와 같이 앱이 직접 선언합니다.
   - 실제 버전은 `gradle/libs.versions.toml`에 정의되어 있습니다. 새 의존성은 먼저 이 파일에 추가한 뒤 `libs.<alias>`로 참조합니다.

    b. minSdk 및 targetSdk 설정 확인
   - build.gradle 파일의 android 섹션에서 minSdk 및 targetSdk 설정을 확인하고, 프로젝트 요구사항에 맞게 수정합니다.

    ```groovy
    android {
        namespace 'org.omnione.did.ca'
        compileSdk {
            version = release(36) {
                minorApiLevel = 1
            }
        }

        def major = 3
        def minor = 0
        def patch = 0

        defaultConfig {
            applicationId "org.omnione.did.ca"
            minSdk 26
            targetSdk 36
            versionCode major * 10000 + minor * 100 + patch
            versionName "$major.$minor.$patch"
        }

        compileOptions {
            sourceCompatibility JavaVersion.VERSION_21
            targetCompatibility JavaVersion.VERSION_21
        }
    }
    ```

4. Import 및 사용

    a. 사업자 URL 수정
   - 각 사업자의 URL은 `app/build.gradle`의 `buildConfigField`로 정의되어 있습니다. `url` 값을 사용 환경에 맞게 수정합니다:
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
   - 런타임에는 이 값들이 `AppConfig` record(`data/config/AppConfig.java`)를 통해 Hilt로 주입됩니다.

    b. did-client-sdk-aos 모듈 사용
   - 사용할 클래스나 메서드가 있는 Java/Kotlin 파일의 최상단에 다음과 같이 임포트합니다:
    ```java
    import org.omnione.did.sdk.core.api.WalletApi;
    import org.omnione.did.sdk.core.exception.WalletCoreException;
    import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
    import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
    import org.omnione.did.sdk.utility.Errors.UtilityException;
    import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;
    ```

   - 이제 did-client-sdk-aos 모듈에서 제공하는 기능을 소스 코드에서 사용할 수 있습니다:
    ```java
    try {
        WalletApi walletApi = WalletApi.getInstance(context);

        String hWalletToken = walletApi.createLocalWalletToken(
                WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC, context.getPackageName());

        List<VerifiableCredential> credentials = walletApi.getAllCredentials(hWalletToken);

        if (credentials != null) {
            for (VerifiableCredential vc : credentials) {
                Log.d("VC", vc.toJson());
            }
        }

    } catch (WalletException e) {
        Log.e("WalletException", "Message: " + e.getMessage());
    } catch (WalletCoreException e) {
        Log.e("WalletCoreException", "Message: " + e.getMessage());
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

        - Correct Dependencies: `build.gradle` 파일과 `gradle/libs.versions.toml`에서 종속성이 정확하게 설정되었는지 확인합니다.
        - SDK Version: 프로젝트의 minSdk 및 targetSdk가 사용 중인 SDK와 호환되는지 확인합니다.
        - Permission: AndroidManifest.xml에서 필요한 권한이 제대로 설정되어 있는지 확인합니다.

<br>

## 프로젝트 구조
```
source/did-ca-aos/
└── app/src/main/java/org/omnione/did/ca/
    ├── config/          상수
    ├── data/
    │   ├── config/      AppConfig
    │   ├── datasource/  로컬 저장소
    │   ├── model/       도메인 모델
    │   ├── network/     HTTP 클라이언트 및 프로토콜 오퍼레이션
    │   ├── repository/  도메인 Repository
    │   ├── sdk/         did-client-sdk-aos 게이트웨이
    │   └── statuslist/  Token Status List 처리
    ├── di/              Hilt 모듈
    ├── protocol/        OID4VCIProtocol, OID4VPProtocol
    ├── ui/<feature>/    화면 단위 패키지 (Activity + ViewModel + UiState)
    └── util/            유틸리티
```

각 화면은 `ui/<feature>/` 아래에 Activity 와 전용 ViewModel · UiState 로 구현되어 있습니다.

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
