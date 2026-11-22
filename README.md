# Carousel Maker

An Android app (Kotlin + Jetpack Compose) for making seamless Instagram carousels, in the style of SCRL.
Built for a Samsung Galaxy S24 but works on any Android 8.0+ phone.

## What it does

- **One seamless canvas.** Slides sit side by side; dashed lines show where Instagram cuts them. Photos, stickers and text can run across a cut.
- **Photos and videos.** Drag, resize (corner and side handles), rotate, pinch, flip, crop (ratio presets, rotate 90), replace, duplicate, reorder layers. Snapping to margins, centres and slide edges.
- **Preset layouts** for 1, 2 and 3 slide spans. Adjust outer margin, gap and corner radius live, plus a switch for margins at slide seams. Per-slide or all-slide backgrounds.
- **Stickers.** 48 hand-sketched stickers (cities, hiking, endurance, travel, everyday, doodles) with an Ink + wash, Ink or Pencil look; tape and paper pieces; import your own PNG.
- **Text** with 15 bundled fonts, size, spacing, alignment, shadow/label/outline effects, colours and ALL CAPS.
- **Torn edges** (torn, deckle, wavy, pinking, fade) with a paper rim, and **slide transitions** (torn strip, tape, paper strip, fold shadow).
- **Slide manager:** add, duplicate, move, delete.
- **Export** to the gallery as JPG or PNG at 1080 px wide (Pictures > Carousel Maker > project name). Slides with a video export as an MP4 with the rest of the slide laid around the clip. Files are timestamped so slide 1 is the newest in the gallery, so selecting them in Instagram keeps the order. One-tap Open Instagram and Share.
- Everything saves automatically on the phone. Unlimited undo and redo.

### Known limits

- One video per slide is composited into the MP4 (extra videos on the same slide show as still frames). A video that is rotated is exported unrotated. No speed control. Clips are limited to 60 seconds.
- "Cut out of photo" (background removal for stickers) is not included yet.
- The app was written and compile-checked without a physical device, so please report anything that behaves oddly.

## Install it on your phone

Easiest: take the ready-built APK **`CarouselMaker.apk`** (a copy was put in your Downloads folder; you can also rebuild it, see below).

1. **Get the file onto the phone.** Any of these:
   - USB cable: plug the phone in, choose "File transfer", and drag `CarouselMaker.apk` into the phone's `Download` folder.
   - Or upload it to Google Drive / email it to yourself and download it on the phone.
2. **Allow the install.** Open the file on the phone (My Files > Download > `CarouselMaker.apk`). Android will ask to allow installs from that app (My Files, Drive or Chrome): tap **Settings** and switch on **Allow from this source**, then go back.
   - On a Galaxy S24, if you see "Blocked by Auto Blocker": go to **Settings > Security and privacy > Auto Blocker** and switch it off (you can switch it back on afterwards).
   - If Play Protect says the app is unrecognised, tap **More details > Install anyway**. That is normal for an app that is not from the Play Store.
3. Tap **Install**, then **Open**.

First launch: tap **New carousel**, choose a format, and pick your photos.

### Or install over USB from the PC

1. On the phone: **Settings > About phone > Software information**, tap **Build number** 7 times, then **Settings > Developer options > USB debugging** on.
2. Plug in the phone and accept the "Allow USB debugging" prompt.
3. On the PC: `C:\Users\conno\android-dev\sdk\platform-tools\adb.exe install -r CarouselMaker.apk`

### Or download it from GitHub

Every push to `main` builds the app. Open the repository on GitHub, then **Actions > Build APK > latest run > Artifacts > CarouselMaker-debug-apk**, download on the phone, unzip and install as above.

## Build it yourself

Needs JDK 17 and the Android SDK (platform 35). With them installed:

```
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest    # unit tests for layouts, slides and transitions
```

(On the original PC the toolchain lives in `C:\Users\conno\android-dev`; run `source /c/Users/conno/android-dev/env.sh` first in Git Bash.)

The release build is signed with the debug key so it installs without any extra setup. To update the app later, install the new APK over the old one (same key, your carousels are kept).

## How it is organised

```
app/src/main/java/app/carouselmaker/
  data/     project model, layout templates and cell maths, slide/seam operations, storage, media import
  render/   one shared renderer used by the editor, thumbnails and export; stickers, tape/paper, text, torn edges
  export/   gallery export and the video compositor (Media3 Transformer)
  ui/       home, new carousel, editor (canvas gestures + panels), export
tools/convert-stickers.js   turns the SVG sketches in tools/wireframe-stickers.html into assets/stickers.json
```

## Credits

Fonts (bundled): Caveat, Permanent Marker, Shadows Into Light, Reenie Beanie, Bebas Neue, Anton, Archivo Black, Oswald, Playfair Display, DM Serif Display, Fraunces, Special Elite, Courier Prime, Inter and Space Grotesk, all from Google Fonts under the SIL Open Font License or Apache 2.0.
