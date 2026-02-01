package data_processing.com.flink.model;

import org.apache.flink.api.java.utils.ParameterTool;
import java.io.Serializable;

/**
 * POJO class encapsulating all BUSINESS LOGIC settings.
 * Easily serialised and transferred to TaskManagers.
 */
public class ProcessingConfig implements Serializable {

    // --- Quality Control ---
    public final long rateLimitMs;
    public final int spamThresholdCount;
    public final long spamAlertCooldownMs;
    public final long badDataWindowMs;
    public final int badDataThreshold;

    // --- Business Logic ---
    public final double absMinTemp;
    public final double absMaxTemp;
    public final long timeoutStuckLow;
    public final long timeoutStuckHigh;
    public final long timeoutHeartbeat;
    // --- Sensitivity Thresholds ---
    public final double tempChangeThreshold;
    public final double largeDeviationThreshold;

    public ProcessingConfig(Builder builder) {
        this.rateLimitMs = builder.rateLimitMs;
        this.spamThresholdCount = builder.spamThresholdCount;
        this.spamAlertCooldownMs = builder.spamAlertCooldownMs;
        this.badDataWindowMs = builder.badDataWindowMs;
        this.badDataThreshold = builder.badDataThreshold;
        this.absMinTemp = builder.absMinTemp;
        this.absMaxTemp = builder.absMaxTemp;
        this.timeoutStuckLow = builder.timeoutStuckLow;
        this.timeoutStuckHigh = builder.timeoutStuckHigh;
        this.timeoutHeartbeat = builder.timeoutHeartbeat;
        this.tempChangeThreshold = builder.tempChangeThreshold;
        this.largeDeviationThreshold = builder.largeDeviationThreshold;
    }

    /**
     * Factory method for creating a configuration from Flink launch parameters.
     * Here we set the default values.
     */
    public static ProcessingConfig fromParameters(ParameterTool params) {
        return new Builder()
                // Quality Control Defaults
                .setRateLimitMs(params.getLong("qc.rate.limit.ms", 5000L))
                .setSpamThresholdCount(params.getInt("qc.spam.threshold", 10))
                .setSpamAlertCooldownMs(params.getLong("qc.spam.cooldown.ms", 30000L))
                .setBadDataWindowMs(params.getLong("qc.bad.data.window.ms", 300000L))
                .setBadDataThreshold(params.getInt("qc.bad.data.threshold", 5))

                // Business Logic Defaults
                .setAbsMinTemp(params.getDouble("logic.temp.min", -40.0))
                .setAbsMaxTemp(params.getDouble("logic.temp.max", 100.0))
                .setTimeoutStuckLow(params.getLong("logic.timeout.stuck.low", 3600000L))
                .setTimeoutStuckHigh(params.getLong("logic.timeout.stuck.high", 300000L))
                .setTimeoutHeartbeat(params.getLong("logic.timeout.heartbeat", 600000L))
                .setTempChangeThreshold(params.getDouble("logic.threshold.change", 0.5))
                .setLargeDeviationThreshold(params.getDouble("logic.threshold.deviation", 10.0))
                .build();
    }

    @Override
    public String toString() {
        return "ProcessingConfig{" +
                "rateLimitMs=" + rateLimitMs +
                ", absMinTemp=" + absMinTemp +
                ", absMaxTemp=" + absMaxTemp +
                '}';
    }

    /**
     * Builder Class
     */
    public static class Builder {
        private long rateLimitMs;
        private int spamThresholdCount;
        private long spamAlertCooldownMs;
        private long badDataWindowMs;
        private int badDataThreshold;
        private double absMinTemp;
        private double absMaxTemp;
        private long timeoutStuckLow;
        private long timeoutStuckHigh;
        private long timeoutHeartbeat;
        private double tempChangeThreshold;
        private double largeDeviationThreshold;

        public Builder setRateLimitMs(long rateLimitMs) {
            this.rateLimitMs = rateLimitMs;
            return this;
        }

        public Builder setSpamThresholdCount(int spamThresholdCount) {
            this.spamThresholdCount = spamThresholdCount;
            return this;
        }

        public Builder setSpamAlertCooldownMs(long spamAlertCooldownMs) {
            this.spamAlertCooldownMs = spamAlertCooldownMs;
            return this;
        }

        public Builder setBadDataWindowMs(long badDataWindowMs) {
            this.badDataWindowMs = badDataWindowMs;
            return this;
        }

        public Builder setBadDataThreshold(int badDataThreshold) {
            this.badDataThreshold = badDataThreshold;
            return this;
        }

        public Builder setAbsMinTemp(double absMinTemp) {
            this.absMinTemp = absMinTemp;
            return this;
        }

        public Builder setAbsMaxTemp(double absMaxTemp) {
            this.absMaxTemp = absMaxTemp;
            return this;
        }

        public Builder setTimeoutStuckLow(long timeoutStuckLow) {
            this.timeoutStuckLow = timeoutStuckLow;
            return this;
        }

        public Builder setTimeoutStuckHigh(long timeoutStuckHigh) {
            this.timeoutStuckHigh = timeoutStuckHigh;
            return this;
        }

        public Builder setTimeoutHeartbeat(long timeoutHeartbeat) {
            this.timeoutHeartbeat = timeoutHeartbeat;
            return this;
        }

        public Builder setTempChangeThreshold(double tempChangeThreshold) {
            this.tempChangeThreshold = tempChangeThreshold;
            return this;
        }
        public Builder setLargeDeviationThreshold(double largeDeviationThreshold) {
            this.largeDeviationThreshold = largeDeviationThreshold;
            return this;
        }

        public ProcessingConfig build() {
            return new ProcessingConfig(this);
        }
    }
}