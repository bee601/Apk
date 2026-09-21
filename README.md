# Look Away Shield — Pixel 10 prototype

This is a prototype Android project for a gaze-triggered privacy shield.

## What it does
- Uses the front camera and Google ML Kit face detection locally.
- Estimates whether the face is roughly oriented toward the phone.
- After ~280 ms looking away, a full-screen overlay animates in.
- Looking back animates it away.
- The overlay uses a dark translucent/frosted visual effect or a user-selected image.
- The setup screen shows permission state and uses a short entrance animation.
- Three shield styles are available: Midnight, Aurora, and Paper, each accepting a custom hex accent.
- Protection is limited to Snapchat, YouTube, Instagram, and launcher apps; other apps are left untouched.

## Important limitation
A normal third-party Android app cannot reliably blur the actual pixels rendered by every other app. This prototype therefore uses a fullscreen overlay. It is visually similar to a privacy blur but does not capture and Gaussian-blur the underlying app.

It also requires:
1. Camera permission.
2. "Display over other apps" permission.
3. A foreground camera service.

Android will show a sensitive-access warning for the camera and overlay permissions.
Target-app mode requires Android's App-Nutzungsdaten/usage-access setting so the
service can identify the foreground package. It only activates for Snapchat,
YouTube, Instagram, and launcher packages; it does not activate for banking or
authentication apps. Frames are analyzed on-device by ML Kit and are not uploaded
by this prototype.

Build with Android Studio using a recent Android SDK. The project targets SDK 35 and Android 10+.
