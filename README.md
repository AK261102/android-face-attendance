# Attendance — Face-Verified Staff Attendance (Android)

An Android attendance app with two roles, **Admin** and **Staff**. The admin adds staff members
and enrols their faces; a staff member marks attendance by taking a selfie, which is verified
against their enrolled face before any record is written.

**Attendance is recorded only when the face matches.** That rule lives in a single place
(`AttendanceRepository.markAttendance`) — every other branch returns a result object and writes
nothing.

---

## Technology & architecture

| Layer | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Architecture | MVVM — Compose screens → ViewModel (`StateFlow`) → Repository → Room/DAO |
| Camera | CameraX (`Preview` + `ImageCapture`), front camera only |
| Face **detection** | ML Kit Face Detection — locates and crops the face |
| Face **recognition** | FaceNet (`facenet_512.tflite`) via TensorFlow Lite — 512-d embedding |
| Matching | Cosine similarity on L2-normalised embeddings, threshold **0.60** (measured, see below) |
| Database | Room (SQLite), local-only |
| Selfie storage | App-internal storage (`filesDir/selfies`), JPEG |
| Location | Play Services `FusedLocationProviderClient` |
| Navigation | Navigation-Compose |
| DI | Manual container in `AttendanceApp` — the graph is one repository and three collaborators |

### Why detection and recognition are separate

ML Kit gives you a **bounding box**, not an identity — it can tell you *a* face is present but not
*whose* it is. So the pipeline is two stages:

```
frame → ML Kit detect → square crop → FaceNet (TFLite) → 512-d vector → cosine similarity → match?
```

Enrolment captures **3 shots**, embeds each, and stores the averaged unit vector as the staff
member's template — averaging makes it noticeably more tolerant of pose and lighting than a
single capture. Marking attendance embeds the selfie the same way and compares against that
stored template (1:1 verification).

### Validating the recognition pipeline

A face model is easy to wire up *almost* correctly: if the preprocessing convention is wrong,
the model still runs and still returns 512 plausible-looking floats — but they collapse, every
face matches every other face, and the app silently records attendance for the wrong person.
Unit-testing the cosine arithmetic does not catch this, because that arithmetic is correct
either way.

So the pipeline is measured directly against real photos — two of the same person taken years
apart, and one of a different person — in two places:

- **`app/src/androidTest/.../FaceEmbedderInstrumentedTest.kt`** runs the real model on a device
  through the actual Kotlin `FaceEmbedder`, and asserts that the same person clears the
  threshold, that different people fall well below it, and that the embedding space has not
  collapsed. **5/5 passing** on an API 34 emulator.
- **`tools/validate_embeddings.py`** does the same comparison in Python, and was used to choose
  the threshold by sweeping the three plausible preprocessing conventions:

| preprocessing | same person | different people | gap |
|---|---|---|---|
| **per-image standardize** (what the app uses) | **+0.742** | +0.021 / −0.001 | **+0.721** |
| `[0,1]` | +0.787 | +0.075 / +0.125 | +0.662 |
| `[-1,1]` | +0.797 | −0.012 / +0.119 | +0.677 |

Different people land at ≈ 0 while the same person — across a change of year, pose, expression
and lighting — scores 0.74. That gap is what makes the threshold meaningful, and **0.60** was
chosen from it rather than guessed.

### Project layout

```
app/src/main/java/com/sb/attendance/
├── data/
│   ├── db/            Room entities, DAOs, database
│   ├── repo/          AttendanceRepository — the only place attendance is written
│   └── SelfieStorage  JPEG persistence to internal storage
├── face/
│   ├── FaceDetectorHelper     ML Kit detection + square crop
│   ├── FaceEmbedder           TFLite FaceNet → embedding
│   ├── FaceMatcher            cosine similarity, threshold, template averaging (pure Kotlin)
│   └── FaceRecognitionService the full pipeline
├── location/          LocationProvider
└── ui/                6 screens + ViewModels + navigation
```

`FaceMatcher` is deliberately free of Android dependencies so the matching rule is unit tested
on the JVM (`app/src/test/.../FaceMatcherTest.kt`, 6 tests). Those cover the decision rule;
the model itself is covered by an on-device test — see below.

---

## Screens

Exactly the six screens the brief asks for:

1. **Login**
2. **Admin — Staff List** (enrolment status shown per member)
3. **Admin — Add Staff** (name + Employee ID)
4. **Admin — Face Enrolment** (3-shot capture, front camera)
5. **Staff — Mark Attendance** (selfie → verify → record)
6. **Admin — Staff Profile / Attendance History** (selfie, date, time, latitude, longitude per record)

---

## Demo credentials

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| Staff | *the staff member's Employee ID* | `staff123` |

Staff accounts are not pre-seeded — a staff member exists only once the admin creates them.

### Walkthrough

1. Sign in as `admin` / `admin123`.
2. **Add staff** → enter a name and an Employee ID (e.g. `EMP001`) → **Save and enrol face**.
3. On the profile, tap **Enrol face** → capture 3 shots of the face.
4. Sign out, then sign in as `EMP001` / `staff123`.
5. Tap **Mark attendance** → take a selfie.
   - Same face → record saved, with selfie, date, time and coordinates.
   - **A different face → rejected, and nothing is written.**
6. Sign back in as admin → open that staff member → the attendance history is there.

To see the rejection path, enrol one person and then have a different person take the selfie.

---

## How to run

**Requirements:** Android Studio (Ladybug or newer), JDK 17, Android SDK 35, a device or
emulator on API 24+.

```bash
git clone <this-repo>
cd android-face-attendance
./gradlew assembleDebug              # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest          # matching-logic unit tests (JVM)
./gradlew connectedDebugAndroidTest  # on-device face-recognition tests (needs a device/emulator)
./gradlew installDebug               # install onto a connected device
```

Or open the project in Android Studio and press Run.

Grant **Camera** when prompted (required) and **Location** (optional — see limitations).

### Emulator note

An emulator's front camera renders a synthetic scene, not a real face, so enrolment and matching
cannot be demonstrated meaningfully on a stock AVD. Either use a physical device, or point the
AVD's front camera at your Mac's webcam:

```
Android Studio → Device Manager → Edit AVD → Advanced → Front Camera → Webcam0
```

---

## What was verified

Run on an API 34 arm64 emulator (Pixel 6 profile):

| Check | Result |
|---|---|
| App launches, no crash | MainActivity displayed |
| Login (admin) -> Staff List | works |
| Add staff -> Room insert -> profile | works, row survives app reinstall and emulator reboot |
| Enrolment screen binds the **front** camera | CameraX preview live, capture button enabled |
| Capture -> ML Kit detection -> rejection path | "No face detected", counter correctly stays at 0/3 |
| FaceNet model loads from assets on-device | 160x160 in, 512-d out |
| Same person vs different people | separated; 5/5 instrumented tests pass |
| Staff login by Employee ID | session binds to that one staff row |
| Unknown Employee ID | rejected with a clear message |
| Mark attendance -> selfie -> verification | runs; **nothing written** when it fails |
| Not-enrolled rejection path | "Not recorded - your face has not been enrolled yet" |
| Unit tests (matching rule) | 6/6 pass |

**Not verified end to end:** a real enrol -> match on a live human face. An emulator's front
camera renders a synthetic scene, so ML Kit correctly finds no face in it. The recognition
maths is covered instead by the on-device test above, which feeds real face images through the
real model. Running the app on a physical device is the remaining step.

## Assumptions & limitations

**Assumptions**

- **Dummy authentication**, as the brief permits. There is no password hashing, token, or account
  store; a staff member signs in with their Employee ID, which binds the session to exactly one
  staff row so verification is 1:1 against that person's enrolled face.
- **Local-only storage.** Room plus internal storage, no backend. The brief allows any reasonable
  database, and this keeps the app fully offline and removes infrastructure from the demo.
- Session is in-memory — restarting the app returns to Login.
- One enrolled template per staff member; re-enrolling replaces it.
- Attendance is a single mark per tap; there is no check-in/check-out pairing, as the brief
  does not ask for one.

**Limitations**

- **No liveness / anti-spoofing.** A printed photo or a picture on another phone held to the
  camera would pass. Real deployments need a liveness check (blink, head-turn, or a depth/IR
  sensor); that is a project in itself and is out of scope here.
- **The 0.60 threshold is measured, but on three photos — not a FAR/FRR curve.** See
  "Validating the recognition pipeline" above for the numbers. Three images is enough to prove
  the pipeline separates people and to place the threshold sensibly; it is not enough to quote
  a false-accept rate. It is a single constant (`FaceMatcher.MATCH_THRESHOLD`) and every screen
  displays the actual similarity score, so the behaviour is easy to inspect and retune.
- **Location is best-effort and never blocks attendance.** If the permission is denied or no fix
  is available, the record is still saved with null coordinates and both the staff screen and the
  admin history show this explicitly. The alternative — refusing attendance because GPS is
  unavailable indoors — seemed worse. Change `AttendanceRepository.markAttendance` if the
  opposite policy is wanted.
- **Recognition accuracy depends on enrolment conditions.** Poor light or an extreme angle at
  enrolment produces a weak template. The enrolment screen asks for 3 shots at varying angles to
  mitigate this.
- **Face data is stored unencrypted** in the app's private database. It is inaccessible to other
  apps on a non-rooted device, but production use would warrant SQLCipher plus a considered
  biometric-data retention policy.
- **APK size (~75 MB)** is dominated by the 24 MB FaceNet model and ML Kit's native libraries,
  and by the debug build being unminified. A release build with R8 and on-demand ML Kit model
  download would cut this substantially.
- Built for **arm64-v8a and x86_64** — all modern physical devices and desktop emulators.
  32-bit ABIs are excluded to keep the APK smaller.

---

## Model attribution

`app/src/main/assets/facenet_512.tflite` is a FaceNet model producing 512-dimensional embeddings,
taken from the public [FaceRecognition_With_FaceNet_Android](https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android)
project. `FaceEmbedder` reads its input and output shapes from the interpreter at runtime, so
substituting a different FaceNet-style model (e.g. MobileFaceNet at 112×112 / 192-d) requires no
code change.

---

## Submission notes

- **Conversation export** — `docs/conversation.json` (471 messages). Regenerate with:

  ```bash
  python3 tools/export_conversation.py \
    ~/.claude/projects/<project-slug>/<session-id>.jsonl \
    docs/conversation.json
  ```

  The script strips base64 image payloads and truncates very long tool outputs, leaving a
  readable JSON transcript.
- **APK** — attached to the [v1.0 release](../../releases/tag/v1.0), and reproducible with
  `./gradlew assembleDebug`.
