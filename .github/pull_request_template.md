## Summary

- 

## Site or behavior changed

- Source/site:
- Example chapter URL:
- Parser path: direct fetch / WebView fallback / both

## Checklist

- [ ] Added or updated a focused `DirectChapterFetcherTest` fixture.
- [ ] Verified the first narrated line starts at real chapter prose.
- [ ] Verified ads, comments, page chrome, and unrelated controls are excluded.
- [ ] Verified previous and next chapter URLs resolve correctly.
- [ ] Ran `./gradlew testDebugUnitTest`.
- [ ] Ran `./gradlew assembleDebug` if source code changed.
