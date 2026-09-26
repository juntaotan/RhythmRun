# Project Design Document

# Product scope

`RhythmRun` records wrist motion and heart rate while running. The Galaxy Watch guides a user-selected running rhythm with vibration and works without the phone. The paired phone provides live analysis, session summaries and a calendar of past runs for long-term tracking. Outdoor GPS route capture and display over a simplified road map are optional enhancements.  
Each session follows four planned stages: warm-up → running → slow-down → recovery. The user sets stage durations and target step cadence before starting, and may pause or advance a stage on the watch. The app records actual stage boundaries, cadence, heart-rate response and wrist movement for each stage. Stage boundaries come from the plan or user action; heart rate alone does not determine the stage.  
The interface distinguishes step cadence (steps/min, supplied by Health Services or calculated from its step counts), estimated wrist rhythm (cycles/min), heart-rate response (observed bpm over time) and movement intensity (wrist acceleration/rotation magnitude in physical units). Wrist cycles cannot automatically be equated to steps. Movement intensity describes wrist motion and is not a physiological exertion score. The route is an optional location record; step count or wrist motion alone cannot reconstruct a real path.

# Architecture

|Parts|Primary ownership|Concrete deliverables|
| ----------------------------------------------| -----------------------------------------------------------------| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|Part One Watch Sensor & Session Control|Watch UI, running stages, sensor capture and vibration guidance|Start/pause/resume/stop; accelerometer, gyroscope, short in-session MeasureClient heart-rate capture and longer ExerciseClient workout capture; exercise step count, optional direct step rate and optional outdoor GPS; stage and cue scheduling; standalone status. Supplies typed readings, stage changes and cue events to Member 2.|
|Part Two Watch–Phone Communication|Shared per-type data format and Wear OS Data Layer|Transfer acceleration, gyroscope, heart rate, exercise step counts, optional direct step rate, optional GPS positions, stage changes and cue events; expose sync status; handle offline delivery and duplicates. Pass received records to Member 4's repository.|
|Part Three Rhythm Processing & Analytics|Phone-side analysis and performance definitions|Motion filtering, confidence-aware wrist-rhythm estimation, cadence-to-target comparison, movement magnitude, stage-specific HR response and guidance-performance summaries; flag inaccurate GPS points and route gaps. Reads stored data and returns versioned results to Member 4.|
|Part Four Phone App, Visualisation & History|Phone Compose UI and Room persistence|Plan setup, live dashboard, four-stage timeline, session summary, calendar, trends and outdoor route on a simplified map; transactional raw-data and derived-result storage. Shows coverage and missing-data states.|

Member 2 owns the shared definitions of units, fields and timestamps; Member 4 owns Room. All four members agree on those interfaces before implementing independently and can trace a reading from watch callback to calendar detail during the code review.

# Part One. Watch capture and session lifecycle

There are four stages, including Warm-up, Running, Slow-down and recovery.

|Stage|Guidance and recording|
| -------| -------------------------------------------------------------------------------------------------------------------------------|
|**Warm-up**|Start the session and capture sensors; show rhythm and current HR. Vibration may be off or use a gentle user-selected target.|
|**Running**|Vibrate at the configured target cadence; show target against observed cadence or clearly labelled wrist-rhythm estimate.|
|**Slow-down**|Follow a lower target cadence chosen in the plan or changed explicitly by the user; retain the transition in the timeline.|
|**Recovery**|Continue recording for a configurable period and display actual HR change from the end of running when readings permit.|

Motion comes from `SensorManager`​ accelerometer and gyroscope (x/y/z), initially targeting 10–20 Hz subject to actual watch measurements. At Start, begin the session clock and motion listeners. Register `MeasureClient`​ for a brief live heart-rate segment while the watch UI is visible (provisional target: 15 seconds after the first reading, 20-second total timeout); retain its actual in-session readings. Unregister and await completion before starting a supported RUNNING workout with `ExerciseClient`​ for longer HR tracking. Mark the handoff gap. Check for direct STEPS_PER_MINUTE; also capture running STEPS to capture exercise `STEPS_TOTAL`​ (cumulative) or `STEPS`​ (per-interval) and calculate cadence using the appropriate form. Check the chosen exercise's capabilities at runtime. Android describes `MeasureClient`​ as short-lived and `ExerciseClient` as the workout client.

The watch maintains stage and cue timers locally, including when the phone disconnects. Pause freezes the stage timer and vibration schedule; Stop persists a finished or partial session. Show unavailable HR/step-rate data and permission or sensor failures explicitly. Use an appropriate foreground recording lifecycle for a long-running session.

### Outdoor route capture

When the user enables an outdoor route, check the watch's exercise/location capabilities, location permissions and GPS availability; request Health Services `DataType.LOCATION`​ in the supported `ExerciseClient`​ running workout with GPS enabled. Save each fix's latitude, longitude, timestamp and available accuracy, plus availability changes. Route recording begins when that workout starts: the brief `MeasureClient` segment before it may have no GPS points. Keep cadence guidance and the run record working when GPS is denied, unavailable or temporarily inaccurate. Mark pauses and GPS gaps; test location availability and battery use on the issued watch.

## Vibration guidance

The user chooses a target in **steps per minute**. A practical starting pattern is one short cue per **two intended steps**: at 160 steps/min, the cue interval is 750 ms. Schedule it against the watch's monotonic clock; record stage target, cue-enabled periods and intended/actual cue times if feasible. Provide a watch-side on/off control and stop cues on pause. Implement with a device-supported `VibrationEffect`​ and `VIBRATE` permission, then measure whether cues are clear and their battery cost. Vibration may disturb accelerometer/gyroscope readings, so mark cue-adjacent motion as potentially contaminated.

# Part Two. Transfer organised by data type

All records include a sessionId and a timestamp mapped to the same watch session timeline. Store calendar start date/time and timezone separately. The fields below describe the shared contract; they do not prescribe a Kotlin class hierarchy.

|Data type|Fields in reading|Source on watch|
| -----------| ------------------------------------------| ----------------------------------------------------------------|
|`AccelerationReading`<br />|timestamp, x/y/z in m/s²|`Accelerator`​ in `SensorManager`|
|`GyroscopeReading`<br />|timestamp, x/y/z in rad/s|`Acceleration`​ in `SensorManager`|
|`HeartRateReading`<br />|timestamp, bpm, availability|Hear Rate in Health Services|
|`StepCountReading`|timestamp, count, source (`STEPS_TOTAL` cumulative)|A result calculated from the accelerometer and gyroscope data.|
|`StepCadenceReading`|timestamp, `STEPS_PER_MINUTE`​,`STEPS_TOTAL_DERIVED`​,`STEPS_DERIVED`|A result calculated from the accelerometer and gyroscope data.|
|`LocationReading`|timestamp, latitude/longitude in degrees||

Send short batches per type using distinct versioned `DataClient`​ paths such as `/rhythmrun/v1/accel/{sessionId}/{batchIndex}`​ and `/rhythmrun/v1/hr/{sessionId}/{batchIndex}`​. Check the encoded size: a `DataItem`​ payload is limited to **100 KB**. Tune batch interval and urgency against measured phone latency and watch battery draw; if needed, add transient `MessageClient`​ previews for a responsive live phone display while `DataClient` remains the saved record. Distinct paths retain successive updates; disconnected data synchronises later. Persist batches/readings with a stable session/type/sequence key so replay cannot create duplicate history. Keep a bounded watch backlog, agree on a receipt/cleanup rule before deleting transferred items, and flag any lost interval.

For outdoor sessions, add a separate versioned location path, for example `/rhythmrun/v1/location/{sessionId}/{batchIndex}`. Retain geographic coordinates and accuracy alongside the session timeline; store the actual fix time and explicit location gaps so the phone does not connect missing stretches into a misleading route. Include location in the same offline sync and deduplication rules as other readings.

# Part Three. Phone processing

Analyse typed readings in timestamped windows (initial proposal: 10-second windows advancing every 2 seconds), then calibrate on actual running recordings.

1. **Cadence source:**  Prefer supported Health Services `STEPS_PER_MINUTE`​. Otherwise calculate cadence over a time window from consecutive exercise `STEPS`​ totals: `60 × Δtotal / elapsed seconds`​; for example, 26 steps in 10 seconds is 156 steps/min. Align timestamps and exclude pauses and delayed/insufficient readings. If a reliable step-count window is unavailable, filter acceleration and gyroscope to estimate wrist periodicity, check half/double-tempo ambiguity and report **estimated wrist rhythm** with confidence. Never silently convert arm cycles into measured steps.
2. **Rhythm stability:**  Measure variability of accepted step/cycle intervals when enough reliable events exist. Mark sparse, irregular or cue-contaminated windows as uncertain.
3. **Movement intensity:**  Calculate dynamic-acceleration RMS (m/s²) and angular-velocity RMS (rad/s). Align each window with cadence and the planned target so the timeline shows how wrist-movement magnitude changes when rhythm changes. Show variation across stages without inferring a physiological zone.
4. **Heart-rate response:**  Show observed HR and data coverage for each stage. A recovery change is calculated only when readings exist at both comparison times; display the actual interval and gaps. Avoid clinical interpretations.
5. **Guidance performance:**  For valid direct or step-count-derived cadence, calculate error against the stage target and the fraction of reliable running windows inside a displayed tolerance (initial proposal: ±5%). If only wrist rhythm exists, show an explicitly **estimated** comparison. Compare individual step timing with vibration cues only if reliable step-event timestamps are available. Show guidance-on/off coverage; an observed difference by itself does not establish that vibration caused improvement.
6. **Route quality (outdoor only):**  Reject invalid coordinates and label fixes with poor reported accuracy; split the drawn route at pauses, location outages and implausible jumps. Keep raw fixes for inspection. If the route is absent, show an explicit unavailable state in session details.

Recompute overlapping windows after delayed data arrive. Store analysis version, cadence source, confidence and coverage. Validate cadence against manually counted steps; record vibration timing, motion artefacts, latency and incorrect detections before making accuracy claims.

# Part Four. Phone experience, calendar and storage

|Screen|Live/history purpose|
| -------------------| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|Welcome/dashboard|Start or resume paired session; show connection and last session|
|Live dashboard|Third parts:<br />The first part is the Plan (it can be shrunk when the run starts).<br />Second part is Live run and Run summary. Live run can include:<br />- stage and remaining time; <br />- target versus actual steps/min or labelled estimated wrist rhythm; <br />- separate HR and movement traces;<br />- cue and sync status<br />And the run summary can include: Stage durations, cadence source, target adherence, rhythm stability, movement magnitude, HR response, cue coverage and incomplete-data indicators.<br />Third part is to display some data in some cards.<br />|
|History|Session list, weekly volume and comparable within-mode rhythm/stability trends; tap for detailed timeline|

Room stores the session start instant, local date/timezone, plan, state, each raw sensor type, optional location fixes with accuracy, stage changes, cue events, analysis windows and summary. Index by session/time, deduplicate replayed records and preserve raw readings as the basis for recalculation. A run crossing midnight appears on its **start date** while retaining complete timestamps. Compare trends only when cadence sources and data coverage are comparable. The watch remains usable offline; the phone updates its saved view after late data synchronise.

If the optional map is implemented, render a `MaplibreMap`​ in Compose using the OpenFreeMap public style `https://tiles.openfreemap.org/styles/liberty`, then add the recorded coordinates as a line layer. MapLibre Compose renders the map; OpenFreeMap supplies tiles without an API key or billing account for its public instance. The phone needs network access to fetch tiles unless an offline pack is prepared. Show map data attribution in the UI and check the provider's current terms; the public service is offered as-is without an uptime guarantee. Keep the route-shape fallback for tile failures.