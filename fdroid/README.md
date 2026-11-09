# F-Droid submission notes

This folder holds the draft F-Droid build metadata, `app.cofcup.yml`.

It is **not** used by the app build. When submitting the app to the F-Droid main
repository, this file gets copied into a fork of
<https://gitlab.com/fdroid/fdroiddata> at `metadata/app.cofcup.yml` and opened
as a merge request against `master`.

Release checklist (every release):

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Tag the release (`git tag -a vX.Y.Z -m "..."` ; push with `git push --tags`).
3. Update the `Builds:` block in `fdroid/app.cofcup.yml` (versionName, versionCode,
   commit = tag).
4. Update the store listing in `fastlane/metadata/android/en-US/`:
   `changelogs/<versionCode>.txt`, plus screenshots/descriptions if they changed.

Store listing assets live in `fastlane/metadata/android/` (descriptions,
changelogs, screenshots, icon, feature graphic) — the F-Droid index reads them
from there via the "fastlane" metadata format.
