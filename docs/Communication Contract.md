# RhythmRun Communication Contract

This document is the shared interface between the Wear OS module, the phone communication layer, the processing layer and the Room repository.

## Session identity and time

- `sessionId`: a UUID string generated once when a session starts. All records in the same session use the same value.
- `timestamp`: `System.currentTimeMillis()` stored as a `Long`. It is the wall-clock time of the reading on the watch.
- `sequence`: a `Long` starting at `0` and increasing by one for each batch of each data type.
- `batchIndex`: the zero-based batch number in the Data Layer path.
- `dataType`: the type of the record, such as `accel`, `gyro`, `hr`, or `steps`.

If elapsed intervals are needed for analysis, store a separate monotonic elapsed time. Do not use a UUID as a timestamp.

## Units

| Data | Unit |
| --- | --- |
| Accelerometer x/y/z | `m/s^2` |
| Gyroscope x/y/z | `rad/s` |
| Heart rate | `bpm` |
| Cadence | `steps/min` |
| Location latitude/longitude | degrees |
| Location accuracy | metres |

## Versioned Data Layer paths

Each data type has its own path so that update rates and offline batches can be handled independently:

```text
/rhythmrun/v1/accel/{sessionId}/{batchIndex}
/rhythmrun/v1/gyro/{sessionId}/{batchIndex}
/rhythmrun/v1/hr/{sessionId}/{batchIndex}
/rhythmrun/v1/steps/{sessionId}/{batchIndex}
/rhythmrun/v1/cadence/{sessionId}/{batchIndex}
/rhythmrun/v1/location/{sessionId}/{batchIndex}
```

The location path is optional. A DataItem must remain below the Wear OS 100 KB payload limit. The initial target is a short batch of about 10 readings or about one second of data, then tune this using measured latency and battery use.

## Reading fields

Every reading includes:

```text
sessionId: String
timestamp: Long
sequence: Long
```

### AccelerationReading

```text
x: Float       // m/s^2
y: Float       // m/s^2
z: Float       // m/s^2
```

Source: `SensorManager` accelerometer.

### GyroscopeReading

```text
x: Float       // rad/s
y: Float       // rad/s
z: Float       // rad/s
```

Source: `SensorManager` gyroscope.

### HeartRateReading

```text
bpm: Float
available: Boolean
```

Source: Health Services `MeasureClient` or `ExerciseClient`.

### StepCountReading

```text
count: Long
source: String  // STEPS_TOTAL, STEPS_PER_MINUTE, or ESTIMATED
```

Health Services step data is preferred. Sensor-based estimates must be labelled `ESTIMATED` and must not be presented as measured steps.

### CadenceReading

```text
stepsPerMinute: Float
source: String       // STEPS_PER_MINUTE, STEPS_TOTAL_DERIVED, or ESTIMATED_WRIST_RHYTHM
confidence: Float    // 0.0 to 1.0
```

### LocationReading (optional)

```text
latitude: Double
longitude: Double
accuracyMetres: Float?
available: Boolean
```

Invalid coordinates, GPS gaps and pauses must be preserved as unavailable states rather than connected into a misleading route.

## Session control messages

`MessageClient` paths:

```text
/rhythmrun/v1/session/start
/rhythmrun/v1/session/pause
/rhythmrun/v1/session/resume
/rhythmrun/v1/session/stop
```

The message payload is the UTF-8 encoded `sessionId`.

The watch owns the local session clock and continues recording while the phone is disconnected. The phone may display a sync status but must not silently change a watch session because a connection is temporarily unavailable.

## Deduplication and delivery

The stable deduplication key is:

```text
sessionId + ":" + dataType + ":" + sequence
```

The phone must ignore a record with a key already stored in Room. A sequence gap must be marked as missing data. Records arriving after reconnection are valid and must be merged into the same session.

`DataClient` is the persisted/offline data channel. A transient `MessageClient` preview may be added for a more responsive live dashboard, but it must not replace the saved DataClient records.

## Ownership boundary

- Member 1 supplies typed watch readings, stage changes and cue events.
- Member 2 serialises, transfers, validates, deduplicates and reports sync status.
- Member 3 analyses validated readings and returns versioned results.
- Member 4 persists raw and derived records in Room and displays them in the phone UI.

All members must use these names, units and paths when implementing their module.
