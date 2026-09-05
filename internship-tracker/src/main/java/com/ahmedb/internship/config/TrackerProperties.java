package com.ahmedb.internship.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tracker behaviour that is policy rather than plumbing. */
@ConfigurationProperties(prefix = "tracker")
public record TrackerProperties(@DefaultValue Ghost ghost, @DefaultValue Digest digest) {

    /**
     * @param thresholdDays silence longer than this marks an application as ghosted
     */
    public record Ghost(@DefaultValue("30") int thresholdDays) {}

    /**
     * @param horizonDays how far ahead the digest looks for deadlines
     */
    public record Digest(@DefaultValue("7") int horizonDays) {}
}
