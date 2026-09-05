package com.ahmedb.internship.domain;

/** Where a {@link StatusEvent} came from. */
public enum EventSource {
    /** Classified from an ingested Gmail message. */
    GMAIL,
    /** Set by the user through the override endpoint. */
    MANUAL,
    /** Written by the application itself (backfill, corrections). */
    SYSTEM
}
