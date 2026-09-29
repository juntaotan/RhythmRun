package com.compx551.rhythmrun.processing

/**
 * The single public entry point for phone-side analytics.
 *
 * It receives persisted-data notifications, coalesces work per session, schedules one worker at
 * a time for that session, and publishes results. Transport decoding, Room entities, UI state and
 * signal-processing mathematics deliberately live outside this class.
 */
class RhythmProcessor(
) {

}
