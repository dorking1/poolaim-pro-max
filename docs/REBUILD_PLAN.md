# PoolAim Pro Max — Rebuild Specification

## Product goal

Rebuild the Android app as a maintainable, offline-first Kotlin application with a polished interface, a user-controlled floating overlay, consent-based screen capture, table/ball detection, calibrated 2D geometry, and a tested trajectory engine. Accuracy must be measured against labelled test frames; never claim pixel-level accuracy or a win-rate without evidence.

## Reference projects reviewed

- `abwahidmoto-beep/PoolAimAssist`: Android/Kotlin reference for MediaProjection capture, overlay service, manual pocket calibration, and trajectory rendering. Review its exact license and attribution requirements before copying any code.
- `carrotdan/ai-billiard-system`: architecture reference for detection, table perspective correction, cue detection, shot scoring, and bank-shot simulation. It is Python-based; port concepts, not its Python runtime.
- `tailuge/billiards`: mature billiards simulation reference with collision, spin, cushion response, tests, and calibration ideas. It is GPL-3.0, so do not copy or port substantial code into a differently licensed app unless the distribution plan complies with GPL-3.0. Use published physics references and independently implement compatible models instead.

No third-party source code, model weights, or assets should be copied until the upstream license and attribution requirements are confirmed.

## Architecture

- **app**: Jetpack Compose UI, onboarding, settings, permission explanation, analysis workflow.
- **capture**: explicit MediaProjection consent flow, foreground service, frame lifecycle, rotation/resolution handling, clear stop action and Android-version compliance.
- **overlay**: draggable control bubble, hide/show, transparent non-touchable drawing layer, touch-through validation, safe teardown and permission recovery.
- **vision**: preprocessing, felt/table candidate detection, perspective homography, ball proposals, color/type classification, confidence scores, temporal tracking and manual correction.
- **calibration**: six-pocket calibration and table geometry; store normalized coordinates per device/game layout; recalibrate after orientation/resolution changes.
- **physics**: normalized coordinate model, ghost-ball geometry, obstruction tests, direct and bank shots, ball-ball and ball-cushion collision simulation, deterministic scoring.
- **analysis**: candidate generation, uncertainty propagation, ranked suggestions, clear confidence/quality labels.
- **history**: local Room history, replay snapshots, settings and privacy controls.
- **quality**: unit tests, image fixtures, golden-output tests, performance benchmarks, lint, CI artifact upload.

## Delivery stages

### Stage 1 — Foundation and build reliability
- Fresh development branch; clean build configuration and CI.
- Navigation and a polished dark/light UI with Arabic RTL and English localization.
- Explicit feature status and permission rationale; no fake controls.

### Stage 2 — Overlay and screen capture
- Explain why permissions are needed; request overlay permission and MediaProjection consent only when the user enables the feature.
- Start/stop foreground service correctly; draggable bubble; hide and restore; persistent notification with stop action.
- Capture frames only during the user-approved session; release projection, reader and virtual display on stop; handle Android 14+ consent rules.

### Stage 3 — Vision and calibration
- Import screenshot and live capture use the same frame pipeline.
- Perspective correction to a normalized table coordinate system.
- Detect cue ball and object-ball candidates; expose confidence; allow tap/drag/add/remove correction.
- Manual six-pocket calibration fallback. Never silently treat guessed geometry as confirmed.

### Stage 4 — Physics and shot planning
- Unit-tested vector/segment geometry and ball radius normalization.
- Direct shots, bank shots, blockers, ghost-ball contact point, collision chains and approximate post-shot cue-ball path.
- Scoring labels geometry quality/difficulty, not an unvalidated probability of success.
- Parameterized friction and cushion restitution; simulation remains approximate until calibrated against reference data.

### Stage 5 — Professional UI and training
- Live overlay toggles for cue path, object path, bank path, blockers and target pocket.
- Fine adjustment, zoom, undo/redo, pause/freeze frame, recalibrate and screenshot review.
- Practice mode, shot history, replay, custom groups/rules and accessible settings.

### Stage 6 — Validation and release
- Build/test/lint on every push and upload debug APK artifact.
- Fixture-based detection tests across varied table colors, resolutions, overlays and occlusions.
- Performance and battery profiling on representative Android devices.
- Test install on a physical device; fix crashes, rotation and permission revocation.
- Release signing only with the owner's own private keystore; never commit signing secrets.

## Acceptance criteria

- Build, unit tests, lint and CI all pass.
- User can start/stop capture, move/hide/restore the bubble, and revoke permissions safely.
- Every live result displays whether the table is calibrated and whether detection is uncertain.
- All screen capture is user-consented, visible, local by default and stopped explicitly.
- Direct/bank shot geometry has unit tests and documented limitations.
- No feature is described as complete until implemented and verified on device.
