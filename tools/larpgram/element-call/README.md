# Embedded call interface

The Android build replaces the assets in Element Call 0.26.1 with a local build
of official revision `9f7c35cd8f84b12f9395e6aac56fd396a3921482` plus
`interface.patch`. The upstream AAR's classes and metadata remain intact.

Prerequisites: Git, Python 3, Node.js 24, pnpm 11 and network access on the first
build. Gradle runs `build.sh`; generated checkouts live under
`.gradle/larpgram-element-call/<patch SHA256>`. No upload or publishing occurs.

Android camera metadata is added only to the embedded appassets configuration.
Recognized Android cameras are grouped into front/rear and standard/wide modes.
Wide mode is offered only when native optical metadata establishes a wider lens.
Unrecognized desktop camera names retain the upstream selection interface.
The raised-hand indicator retains its action and accessible label without a timer.
Experimental native calls do not use these assets.

The patch includes focused unit tests, Storybook interaction coverage and a
standalone Playwright configuration (`playwright/larpgram-camera-controls.config.ts`).
Run them in the patched source checkout with the repository's pinned pnpm:

```sh
pnpm exec vitest run src/state/CameraDeviceLabels.test.ts src/reactions/RaisedHandIndicator.test.tsx src/main.test.tsx
pnpm exec playwright test --config playwright/larpgram-camera-controls.config.ts
```

Browser tests cover the controls without contacting a call server. They do not
establish real-device lens switching or two-party call behavior.
