# AI HANDOFF – GTA San Online launcher / SA-MP Android client

Read this first. It brings an AI assistant up to speed on the project, the owner's way of
working, what has been changed, and what is still broken. Keep it updated at the end of every task.

## 1. Project
- Android app "GTA San Online": launcher (Java, Material Components) + in-game client for
  SA-MP on GTA:SA Android (native C++20 library `libAlyn_SAMPMOBILE.so`, ImGui, RakNet,
  GlossHook inline hooks into `libGTASA.so` arm64, game version 2.10).
- Repo: `tanakorn1680/Gta-Aan-Online-Launcher`, package `com.gtasan.online`, built by GitHub
  Actions (`.github/workflows/`). Native sources: `app/src/main/cpp/Alyn_SAMPMOBILE/`.
- Launcher -> game data flow: launcher writes `SAMP/settings.json` (`Utils.saveSettings`) from
  SharedPreferences `samp_settings` / `samp_server`; native `Settings.cpp` reads that JSON.
- Game activity `game/SAMP.java` runs in its own process (`android:process=":game"`) and ends
  that process when it finishes (same design as the newest reference launcher).

## 2. Owner and working agreements (important)
- Owner writes Thai. Reply in Thai, concise. Owner describes the logic, AI implements, owner
  sends back error messages / client logs for targeted fixes.
- Do ONLY what is asked. No unrequested extras (earlier extras upset the owner). When working for
  a long time, say briefly what you are doing.
- Deliver updates as ONE ZIP containing only the changed/new files, normal repo-relative paths,
  no wrapping folder, so it extracts straight into the repo root over the existing files.
- The AI environment cannot run Gradle/NDK or the game. What can be checked offline: C++ syntax with
  `clang++ -fsyntax-only -std=c++20 -DVER_x32=false -DRAKSAMP_CLIENT -D__ANDROID__` plus stub
  headers for `android/*` and `EGL/GLES`, and the include dirs from the sub-CMakeLists; Java syntax with
  `javac -proc:none`; XML well-formedness. Never claim a build or device test was done.
- Many files have CRLF or mixed line endings. Preserve them when editing (normalise on read, write back
  in the file's dominant style).

## 3. Goal
Bring this project (base "V3" zip) up to, or beyond, the newest reference launcher (alynEX,
`ro.alynsampmobile`, v20.7.8: Flutter launcher + ImGui client) and fix the small bugs, as far as
possible without its source (its native strings are obfuscated; behaviour was studied from its APK
structure and `client.log`). Concrete asks so far: GUARD (fight/guard stance) button and RIDE
(enter nearby vehicle as passenger) button with icons; stop the crash when entering/exiting vehicles;
Thai text support.

## 4. What is in the repo now (on top of V3)
- `Features.h` compile-time switches (current values in brackets):
  `FEATURE_GUARD_BUTTON` [0], `FEATURE_RIDE_BUTTON` [0], `FEATURE_VEHICLE_COOLDOWN` [0] (RIDE press
  cooldown only), `FEATURE_TASK_GUARD` [1], `FEATURE_CRASH_DIAGNOSTICS` [1].
  GUARD/RIDE are OFF only to isolate the vehicle crash; turn them on again once vehicles work.
- GUARD/RIDE: `UI/SAMPWidgets/ButtonPanel.{h,cpp}`, created in `UI/UI.cpp`; icons
  `assets/hud/btn_guard.png`, `btn_door.png` (loaded with `LoadIconTextureFromAsset`); GUARD forces
  `CPad::GetTarget/GetEnterTargeting/GetBlock` while held (`Game/Pad.cpp`, hook for GetTarget only
  installed when the flag is on). The guard stance itself was never confirmed working on device.
- `FEATURE_TASK_GUARD` (`Game/Pad.cpp`): hooks `CTaskManager::SetTask` and `SetNextSubTask`; if the old task
  or a task in the parent chain sits in a FREE slot of the task pool (or the manager's ped is null) it is
  dropped/skipped with a `warn` log instead of crashing. Added after 3 crash logs, not yet confirmed on device.
- Crash diagnostics (`Client.cpp`): chained SIGABRT handler (logs backtrace, then hands over to the
  previous handler so Android still writes a tombstone), `std::set_terminate` logging, log flushed
  every second and on warn. SIGSEGV handler unchanged (logs register dump + backtrace, then `exit`).
- Text encoding: launcher setting "Text encoding" (CP1251 default, UTF-8, CP874 Thai, CP1252, GBK);
  `utils/TextEncoding.java`, JSON key `text_encoding`, native `Encoding::setCharset`
  (`Deps/Encoding`), Java `UI.java`/`Dialog.java` encode typed text with it (V3 hard-coded windows-1251,
  so Thai could not be typed). Non-default code pages also accept valid UTF-8 from the server.
- Thai glyphs: `UI/ImGuiWrapper.cpp` merges a system Thai font (`/system/fonts/NotoSansThai*`) into
  `arial_bold.ttf`.
- Launcher fonts: Chakra Petch (OFL, Thai-capable) bundled in `res/font/`; the old downloadable
  Montserrat/Gotham families now point to it. License in `assets/licenses/`.
- `SAMP.java`: also deletes `models/MINFO.BIN` at start (copied from alynEX behaviour; if model or
  vehicle data looks wrong, revisit this first).
- V3 already contained (owner's own change): lazy BASS init in `Game/AudioStream.*`.

## 5. OPEN BUG (top priority): entering/exiting vehicles
Symptoms from owner: pressing enter vehicle does not enter, pressing exit does not exit, pressing
repeatedly drops the game back to the launcher main page.
Evidence (client logs in `Android/data/com.gtasan.online/files/logcat/client_*.log`):
- Every crash is in the local ped's task system, right after the log line `ExitVehicleJustDown accepted`
  (the hook in `Game/Pad.cpp` returned true):
  - 10-15: abort `__cxa_deleted_virtual` in `CTaskManager::SetNextSubTask`.
  - 10-17: SIGSEGV null deref in `CTaskSimpleStandStill::ProcessPed` called from `SetNextSubTask`
    (ped argument null).
  - 10-18: abort `__cxa_deleted_virtual` in `CTaskManager::SetTask` called from `CPlayerInfo::Process`.
  - Earlier tombstone (V1 era, rapid presses): "Pure virtual function called" – same family
    (virtual call on an already destroyed CTask).
- In those sessions `NotifyEnterVehicle`, `SendEnterVehicleNotification`, `SendExitVehicleNotification`
  never appear, i.e. the enter/exit task hooks are not reached although the pad reported a press.
- A 1 s lock inside the `CPad_ExitVehicleJustDown` hook (added by an AI) made exiting impossible – the game calls
  that function several times per frame; do not gate its return value.
- Possible context: server events (death, skin change, player quit) near the press; three train carriage
  vehicles (type 570) fail with "has no entity, discarding" at join (probably unrelated).
Ideas not yet checked: who frees tasks without clearing the manager slot (client code calls
`..D0Ev` deleting destructors in `Game/PlayerPed.cpp` for jetpack/drive-by); the replaced pools in
`Game/Patches.cpp` (`ms_pTaskPool` etc. are re-created with `new CPool`, any task allocated before the
replacement and freed after it would corrupt the new pool); whether remote peds also go through the
`ExitVehicleJustDown` hook (it has no `*pbyteCurrentPlayer` branch).
Next step: get a log from a build with `FEATURE_TASK_GUARD` on; look for `dropping freed task` /
`SetNextSubTask: skipped` warnings (they prove the guard fired and show timing).

## 6. Reading crash logs
- The client prints a backtrace with symbol names for libGTASA frames. For unnamed frames resolve the offset
  against a reference `libGTASA.so` arm64 with pyelftools/capstone. The reference build matches the owner's
  (`CTaskManager::SetNextSubTask` = 0x64E788). Reference files (alynEX APK, alyn_sampmobile APK) are not in
  the repo; the owner has them.
- Log order inside one second is the real order of events.

## 7. Newest-launcher (alynEX) study – not yet applied
Has: Flutter launcher with many languages (no Thai), server add/refresh, game file verify, MonetLoader/CLEO
script management, crash-log viewer, screenshot gallery, auto reconnect option, FPS, gyro aiming and
per-mode sensitivity, physical keyboard/mouse/gamepad, per-server text-encoding lock, system-font merge for
Thai/Arabic etc., SafeDxtDecode, Xclipse GPU fix, Cortex-A75 errata guard for gun task creation, MatFX
free-list pinning, map limits, gameplay patches. Items needing libGTASA offsets cannot be reproduced blindly.
V3 already has a basic reconnect-on-loss, FPS limit/FPS display, and a TexDB/RLEDecompress DXT crash workaround.

## 8. Possible next tasks (ask the owner first)
Re-enable GUARD/RIDE after vehicles work; launcher UI redesign (needs screenshots of the reference);
auto-reconnect as a setting; crash-log viewer; keyboard/mouse support.
