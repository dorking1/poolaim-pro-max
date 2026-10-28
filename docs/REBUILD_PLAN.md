# PoolAim Pro Max — Rebuild Roadmap

## Current branch
- Working branch: `rebuild/pro-max-v1`
- Target device: Android; user-provided reference screenshots show a landscape 8-ball pool table.
- Current workflow: Android unit tests, lint, and debug APK assembly.

## Implemented foundation
- Floating overlay service with user-controlled overlay permission.
- Reference table profile based on the supplied 1536×720 screenshots.
- Table-local coordinate conversion and six estimated pocket centers.
- OpenCV Hough-circle proposals restricted to the approximate cloth region.
- HSV color-family estimates with confidence values.
- Frame analyzer composing circle proposals, color estimates, and table coordinates.
- Consent-driven MediaProjection foreground service that receives and closes frames.
- CI workflow for unit tests, lint, debug APK assembly, and artifact upload.

## Important limitations
- The table boundaries and pocket positions are estimates; device/layout calibration is still required.
- Hough circles can produce false positives and miss overlapping or blurred balls.
- HSV estimates are not reliable ball identity; number recognition and stripe/solid classification are not implemented.
- Screen capture currently logs frame dimensions only. It is not yet connected to the vision analyzer or an on-screen aim overlay.
- Geometry recommendations require additional collision, cushion, and pocket validation against actual gameplay.

## Next milestones
1. Confirm the latest Android CI build; fix all compile, lint, and unit-test failures.
2. Add unit tests and image-based validation for the vision pipeline.
3. Convert MediaProjection frames to a controlled, reusable OpenCV input format and run analysis off the UI thread.
4. Add an explicit calibration flow for cloth bounds, pockets, and ball radius.
5. Add user-correctable ball candidates and confidence indicators before trusting automatic detection.
6. Validate direct-shot geometry, ghost-ball targets, obstruction checks, and cushion reflections with deterministic tests.
7. Draw proposed paths in a clearly separate overlay and allow users to toggle or dismiss it.
8. Test on the user's Android device and supplied game before producing a release build.

## Quality gate
Do not call the app complete or advertise live aiming as functional until CI passes, capture works on a real device, and the detected table/balls and paths have been checked against representative screenshots and gameplay.
