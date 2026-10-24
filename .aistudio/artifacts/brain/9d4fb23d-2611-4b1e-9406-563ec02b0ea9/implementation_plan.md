# Full-Bleed SamilliMed Launcher Icon Implementation Plan

Plan to configure `app/src/main/res/drawable/samillimed_icon.png` as a full-bleed launcher icon that scales to occupy the complete available icon space without insets, padding, surrounding background rings, or nested frames.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Full-Bleed Foreground**: All `18dp` insets and margins are removed. `samillimed_icon.png` will directly serve as the foreground layer (`108dp x 108dp` full-bleed) across adaptive icon configurations (`res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`).
> - **Elimination of Nested Frames**: The artwork is not shrunk or placed inside a secondary frame. It scales to fill the entire icon space. The launcher's adaptive mask (circle, squircle, or rounded rectangle) will shape the artwork directly, producing the appearance of an integrated icon rather than a picture inside a frame.
> - **Trade-Off / Masking Behavior**: Because `samillimed_icon.png` fills the entire canvas edge-to-edge, the outer subtle curved border naturally aligns with the launcher's OEM mask. The central medical emblem (gold roof, cross, and 'S' caduceus) occupies the middle ~60% of the image, so it remains safely within the central visible area on all launcher masks while the dark green field fills the entire icon.
> - **Legacy Alignment**: Legacy mipmap resources (`res/mipmap/ic_launcher.xml` and `ic_launcher_round.xml`) continue to point directly to `@drawable/samillimed_icon` without insets or modification.

---

## 1. Technical Changes

### Architecture Flow

```
app/src/main/AndroidManifest.xml
   ├── android:icon="@mipmap/ic_launcher"
   └── android:roundIcon="@mipmap/ic_launcher_round"
             │
             ├──► API 26+ (Adaptive Icons)
             │      res/mipmap-anydpi-v26/ic_launcher.xml & ic_launcher_round.xml
             │         ├── <background> -> @color/launcher_icon_background (#012312)
             │         └── <foreground> -> @drawable/samillimed_icon (Full-bleed, 0dp inset)
             │
             └──► Legacy / Fallback
                    res/mipmap/ic_launcher.xml & ic_launcher_round.xml
                       └── <bitmap android:src="@drawable/samillimed_icon" /> (Full-bleed)
```

### File Modifications

1. **`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` & `ic_launcher_round.xml`**:
   - Point `<foreground>` directly to `@drawable/samillimed_icon` (no inset wrapper), allowing the bitmap to scale across the full icon bounds.
2. **`app/src/main/res/drawable/ic_launcher_foreground.xml`**:
   - Update to a direct bitmap referencing `@drawable/samillimed_icon` (or point directly to `@drawable/samillimed_icon` in the adaptive icon XML).
3. **`app/src/main/res/values/launcher_icon_colors.xml`**:
   - Maintain `launcher_icon_background` as `#012312`, matching the dark green field of `samillimed_icon.png`.
4. **`app/src/main/res/mipmap/ic_launcher.xml` & `ic_launcher_round.xml`**:
   - Ensure clean direct bitmap references to `@drawable/samillimed_icon`.

---

## 2. Verification Steps

1. Verify that `app/src/main/res/drawable/samillimed_icon.png` is preserved completely untouched.
2. Build the project using `./gradlew assembleDebug` to verify clean compilation.
3. Inspect the packaged APK to verify resource resolution and ensure no insets or duplicate frames remain.
