package data_processing.com.flink.model;

public enum AlertType {

    /**
     * High percentage of invalid data (broken JSON, schema errors).
     */
    MALFUNCTION,

    /**
     * Excessive message sending frequency (suspected DDoS).
     */
    SECURITY_SPAM,

    /**
     * The temperature is moving in the opposite direction from the setpoint.
     */
    CRITICAL_DIRECTION,

    /**
     * The temperature does not reach the target value within the allotted time.
     */
    STUCK,

    /**
     * No data from the device during the Heartbeat timeout.
     */
    OFFLINE
}
