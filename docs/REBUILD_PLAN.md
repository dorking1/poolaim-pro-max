# PoolAim Pro Max — Rebuild Roadmap

## Current branch
- Working branch: `rebuild/pro-max-v1`
- Target: the supplied landscape 8-ball game screenshots.
- CI workflow: Android unit tests, lint, debug APK assembly, and artifact upload.

## Implemented in source
- Floating bubble and compact control panel.
- Explicit Android MediaProjection consent and foreground capture service.
- On-device OpenCV Hough-circle proposals within the estimated cloth region.
- HSV color-family estimation with confidence values; the frame analyzer reuses one HSV conversion per frame.
- Transparent, non-touchable overlay that draws candidate circles, family colors, and short family labels.
- Estimated table-local coordinates and six pocket centers from the supplied 1536×720 reference.
- Geometry-only direct and one-cushion shot candidate engine with obstruction checks.
- GitHub Actions build/test/lint workflow.

## Limitations — do not treat these as solved
- Current detection circles are proposals, not guaranteed ball detections. Highlights, stripes, shadows, motion blur, and UI elements can cause false positives.
- The table profile is a fixed estimate, not automatic perspective/table calibration.
- Number recognition, reliable stripe/solid grouping, cue-stick detection, tracking across frames, and confidence-gated recommended shot paths are not yet integrated.
- Shot ranking is geometric, not a calibrated probability of potting a ball.
- No claim of accuracy or speed on the target phone is valid until the APK is installed and tested on actual gameplay.

## Next milestones
1. Get a green CI run on the latest commit and resolve any build/test/lint issues.
2. Compare the overlay against the user's actual game screen and measure false positives/missed balls.
3. Add table boundary, pocket, and ball-radius calibration with manual correction.
4. Improve candidate tracking and distinguish the cue ball from white highlights on object balls.
5. Add robust number recognition and stripe/solid classification after calibration stabilizes.
6. Connect the shot geometry engine to confidence-gated detections and draw cue/object paths only when calibration is adequate.
7. Add obstruction warnings, optional bank-shot guidance, adjustable analysis rate, and training/session history.
8. Profile frame time, CPU, and memory on-device before producing a release APK.

## Privacy and Android requirements
Screen analysis is performed locally in this implementation. Android's system consent prompt is required for each new MediaProjection session; the app must not bypass it.
