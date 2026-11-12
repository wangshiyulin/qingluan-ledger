# Third-party notices

## AndroidX Compose Material Icons

The functional line icons used throughout Qingluan Ledger, including the category and settings icons, are from AndroidX Compose Material Icons (`androidx.compose.material:material-icons-extended`). These are open-source Material icons distributed under the Apache License, Version 2.0.

Copyright The Android Open Source Project. The individual source files contain their respective copyright notices.

Sources:
- https://github.com/androidx/androidx/tree/androidx-main/compose/material/material-icons-extended
- https://github.com/google/material-design-icons

License text is bundled in the APK and source tree at `app/src/main/assets/licenses/Apache-2.0.txt`.

This notice applies to those third-party icons. The user-supplied Qingluan brand artwork is separate; this document does not grant third-party rights to that brand artwork or choose a license for the complete application.

Other existing dependencies retain their upstream licenses and notices.

## Android APK Signature Scheme verifier (apksig)

APK update integrity and signer verification use `com.android.tools.build:apksig:8.7.3`, distributed under the Apache License, Version 2.0.

Copyright The Android Open Source Project and respective contributors.

- https://android.googlesource.com/platform/tools/apksig/

The bundled Apache-2.0 license text applies. Signature trust is checked against the currently installed application's signing certificate, not a certificate retrieved from the update server.
