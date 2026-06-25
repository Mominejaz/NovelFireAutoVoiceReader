# Contributing

Thanks for helping improve Novel Voice Reader. The project is focused on personal offline reading and accessibility workflows for chapter pages the user can already access.

## Pull Requests

- Keep pull requests focused on one site, feature, or fix.
- Do not include generated build outputs, release APKs, Android Studio workspace files, local SDK paths, credentials, or personal tokens.
- Do not add code intended to bypass logins, paywalls, access controls, copyright protections, or website terms.
- For new source support, include a small representative HTML fixture in `DirectChapterFetcherTest`.
- For parser changes, verify the first narrated line, ad cleanup, footer/comment cleanup, and previous/next chapter links.
- If WebView fallback behavior changes, keep its selectors and cleanup rules aligned with direct fetch where practical.

## Local Checks

Run these before opening a pull request:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

On Windows with Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## Licensing

By contributing, you agree that your contribution will be licensed under the MIT License used by this repository.
