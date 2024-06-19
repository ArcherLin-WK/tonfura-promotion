package io.archer;

import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetTime;

@ConfigMapping(prefix = "activity", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface ActivityOptions {
    @WithConverter(DurationConverter.class)
    @WithDefault("PT1M")
    Duration issuingDuration();

    @WithDefault("23:00:00+08:00")
    OffsetTime issuingTime();

    @WithConverter(DurationConverter.class)
    @WithDefault("PT4M")
    Duration reservingDuration();

    @WithDefault("22:55:00+08:00")
    OffsetTime reservingTime();

}
