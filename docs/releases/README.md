# Release process

All fixes and features in this repository are versioned and published:

1. Bump `versionCode` and `versionName` in `app/build.gradle` in the same change
   (or in the release commit that immediately follows the fix).
2. Run the JVM unit tests, instrumentation tests when a device/emulator is
   available, lint, and a signed `:app:assembleRelease`.
3. Add `docs/releases/<version>.md` with user-facing release notes.
4. Add evidence under `docs/evidence/apk-<version>/` (APK SHA-256, signing
   certificate, device smoke test, screenshots as appropriate).
5. Commit, tag `v<version>`, push `master` and the tag, and publish a GitHub
   release with `app-universal-release.apk`.
6. Verify the uploaded asset digest and that the release is marked Latest.

Do not leave fixes only in the working tree or pushed without a release. Patch
releases use the next `versionCode` and `0.6.x` version number.
