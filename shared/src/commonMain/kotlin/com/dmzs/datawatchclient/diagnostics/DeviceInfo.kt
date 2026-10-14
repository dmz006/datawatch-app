package com.dmzs.datawatchclient.diagnostics

/** Platform facts that go in the report header. No identifiers, no serial numbers. */
public data class DeviceInfo(
    /** `Android` or `iOS`. */
    val platform: String,
    /** e.g. `14 (API 34)` or `Version 17.5 (Build 21F79)`. */
    val osVersion: String,
    /** Manufacturer and model, e.g. `Google Pixel 8` or `iPhone15,2`. */
    val model: String,
)

/** Reads [DeviceInfo] for the running device. */
public expect fun currentDeviceInfo(): DeviceInfo
