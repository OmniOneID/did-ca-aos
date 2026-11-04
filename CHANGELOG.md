# Changelog

## v3.0.0 (2026-10-30)

### 🚀 New Features
- OID4VC Credential Issuance
    - Added DID-based SD-JWT VC (`dc+sd-jwt-did`) credential issuance.
    - Added DID-based mdoc (`mso_mdoc-did`) credential issuance and display.
    - Renders mdoc portrait image claims in the VC detail and VP review.
    - Added the `tx_code` input for pre-authorized code issuance.
- OID4VP Presentation
    - Added DID-based SD-JWT VC (`dc+sd-jwt-did`) credential presentation.
    - Added DID-based mdoc (`mso_mdoc-did`) credential presentation.
- Presentation Consent
    - Added claim-level selection for OpenDID VC (non-ZKP), OID4VP and proximity presentation; optional claims are opt-in and required claims are always included.
- mdoc Proximity Presentation
    - Added mdoc proximity presentation through the VP consent screen.
    - Supports QR and NFC device engagement.
    - Supports BLE with a selectable BLE transfer mode, and Wi-Fi Aware when the device supports it.
    - Tells the holder why a proximity session ended.
- Credential Status and Management
    - Resolves mdoc credential status via Token Status List.
    - Revokes OID4VC credentials at the issuer before local deletion.
- Wallet Token
    - Issues wallet tokens locally for the credential list, detail and proximity presentation.
- Settings
    - Server URLs can be set on first launch, before wallet bootstrap.
    - Shows the user name in Settings with a copy button.

### 🔧 Build and Dependencies
- Upgraded the Android Gradle Plugin to 9.1.0 and Gradle to 9.4.1, and moved dependencies to the version catalog.
- Raised compileSdk and targetSdk to 36.
- Updated the wallet SDK to `did-wallet-sdk-aos-3.0.0.jar`.
- Removed unused dependencies.

## v2.0.1 (2025-10-13)

### 🚀 New Features
- Add biometric authentication
- development method pattern change in progress

## v2.0.0 (2025-05-27)

### 🚀 New Features
- Add zero-knowledge proof feature
- Integrate client sdk

## v1.0.0 (2024-10-18)

### 🚀 New Features
- Did Document and VC management through mobile wallet.
