#pragma once

// Compile-time switches for the optional extras added on top of the base client.
// If something misbehaves, set the suspect switch to 0 and rebuild to rule it out.

#define FEATURE_GUARD_BUTTON      0  // on-screen GUARD button (hold = guard/fight stance)
#define FEATURE_RIDE_BUTTON       0  // on-screen RIDE button (enter nearby vehicle as passenger)
#define FEATURE_VEHICLE_COOLDOWN  0  // 1 s cooldown between RIDE button presses
#define FEATURE_TASK_GUARD       1  // drop dangling ped tasks instead of crashing in CTaskManager::SetTask/SetNextSubTask
#define FEATURE_CRASH_DIAGNOSTICS 1  // log SIGABRT + uncaught exceptions, flush the log every second
