# OrbitBar implementation plan

**Phase 1 — foundation (delivered in source):** native Android project, manifest, foreground overlay service, permissions setup, three user-selectable configurations, local preferences and notification stop action.

**Phase 2 — launcher and multitasking (delivered in source):** enumerate launchable applications, pinned favourites with long-press editing, recent app ranking through UsageStatsManager with local history fallback, searchable app list, normal/split/freeform-best-effort launch options.

**Phase 3 — media and controls (delivered in source):** user-authorized MediaSession integration, previous/play/pause/next, Internet/Bluetooth/display quick-settings shortcuts and media volume up.

**Phase 4 — build and functional QA (requires Android SDK and test devices):** compile via the included GitHub Actions workflow, verify install and overlay permission flow, verify each layout, pinning, search, usage access, track controls, notifications and graceful fallback on unsupported split/freeform. Test on Pixel 9a, POCO F6 and OnePlus Nord CE5, where possible.

**Phase 5 — polish after testing:** add drag-to-edge gestures, adjustable visual preferences, layout overflow handling, dark/light auto theming, accessibility labels, battery-impact profiling, and multiple-workspace shortcuts.

## Manual QA checklist

- [ ] Build GitHub Actions job completes and uploads an APK
- [ ] Installs and opens on Android 8+ without immediate crash
- [ ] Start without overlay permission opens corresponding settings
- [ ] Overlay appears when allowed; notification Stop removes it
- [ ] Dock/Taskbar/Edge modes all show correctly and switch in app and overlay
- [ ] Pin/unpin persists after restarting app and service
- [ ] Search filters and opens launchable apps
- [ ] Without usage permission, OrbitBar-opened apps appear as recent
- [ ] With usage permission, recent apps are ordered correctly
- [ ] Without media access, no crashes; with media access, controls operate supported players
- [ ] Internet/Bluetooth/display shortcuts open supported system panels
- [ ] Normal, Split and Float work or gracefully fall back without app crashes
- [ ] Screen rotation does not orphan a taskbar window
