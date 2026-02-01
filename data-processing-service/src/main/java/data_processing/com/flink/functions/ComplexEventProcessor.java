package data_processing.com.flink.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import data_processing.com.flink.model.AlertType;
import data_processing.com.flink.model.ProcessingConfig;
import data_processing.com.flink.model.AlertEvent;
import data_processing.com.flink.model.TelemetryEvent;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.TimeDomain;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Complex Event Processor (CEP)
 */
public class ComplexEventProcessor extends KeyedProcessFunction<String, TelemetryEvent, TelemetryEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ComplexEventProcessor.class);

    private final OutputTag<String> alertsTag;
    private final ProcessingConfig config;
    private transient ObjectMapper objectMapper;

    // --- State ---
    private transient ValueState<Double> lastTempState;
    private transient ValueState<Double> targetTempState;
    private transient ValueState<Long> registeredStuckTimerState;
    private transient ValueState<Long> registeredHeartbeatTimerState;
    private transient ValueState<Long> lastHeartbeatProcessingTimeState;
    private transient ValueState<Integer> stuckRepeatCountState;

    // --- Metrics ---
    private transient Counter stuckAlerts;
    private transient Counter offlineAlerts;
    private transient Counter directionAlerts;

    public ComplexEventProcessor(ProcessingConfig config, OutputTag<String> alertsTag) {
        this.config = config;
        this.alertsTag = alertsTag;
    }

    @Override
    public void open(Configuration c) {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        lastTempState = getRuntimeContext().getState(new ValueStateDescriptor<>("lastTemp", Double.class));
        targetTempState = getRuntimeContext().getState(new ValueStateDescriptor<>("targetTemp", Double.class));
        registeredStuckTimerState = getRuntimeContext().getState(new ValueStateDescriptor<>("regStuckTimer", Long.class));
        registeredHeartbeatTimerState = getRuntimeContext().getState(new ValueStateDescriptor<>("regHbTimer", Long.class));
        lastHeartbeatProcessingTimeState = getRuntimeContext().getState(new ValueStateDescriptor<>("lastHbProcTime", Long.class));
        stuckRepeatCountState = getRuntimeContext().getState(new ValueStateDescriptor<>("stuckRepeat", Integer.class));

        stuckAlerts = getRuntimeContext().getMetricGroup().counter("alerts_stuck");
        offlineAlerts = getRuntimeContext().getMetricGroup().counter("alerts_offline");
        directionAlerts = getRuntimeContext().getMetricGroup().counter("alerts_direction");
    }

    @Override
    public void processElement(TelemetryEvent event, Context ctx, Collector<TelemetryEvent> out) throws Exception {
        long eventTime = event.getTimestamp().toEpochMilli();
        long processingTime = ctx.timerService().currentProcessingTime();

        updateHeartbeat(ctx, processingTime);

        double currentTemp = event.getData().getCurrentTemperature();
        Double targetTemp = event.getData().getTargetTemperature();
        Double oldTemp = lastTempState.value();

        checkDirection(ctx, event.getDeviceId(), currentTemp, targetTemp, oldTemp);
        checkStuckOrExtreme(ctx, currentTemp, targetTemp, oldTemp, eventTime);

        lastTempState.update(currentTemp);
        if (targetTemp != null) targetTempState.update(targetTemp);

        out.collect(event);
    }

    // Helper now throws specific IOException
    private void updateHeartbeat(Context ctx, long currentProcTime) throws IOException {
        lastHeartbeatProcessingTimeState.update(currentProcTime);
        Long existingTimer = registeredHeartbeatTimerState.value();
        long newTimeout = currentProcTime + config.timeoutHeartbeat;

        if (existingTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(existingTimer);
        }
        ctx.timerService().registerProcessingTimeTimer(newTimeout);
        registeredHeartbeatTimerState.update(newTimeout);
    }

    private void checkDirection(Context ctx, String deviceId, double current, Double target, Double old) {
        if (old != null && target != null) {
            double tempDiff = current - old;
            double targetDiff = target - current;

            if (Math.abs(tempDiff) > config.tempChangeThreshold) {
                boolean wrongDirection = (targetDiff > 0 && tempDiff < 0) || (targetDiff < 0 && tempDiff > 0);
                if (wrongDirection) {
                    String msg = String.format("Wrong direction! Target: %.1f, Current: %.1f -> %.1f", target, old, current);
                    sendAlert(ctx, deviceId, AlertType.CRITICAL_DIRECTION, msg, current, directionAlerts);
                    LOG.warn("Device {} {}", deviceId, msg);
                }
            }
        }
    }

    // Helper now throws specific IOException
    private void checkStuckOrExtreme(Context ctx, double current, Double target, Double old, long eventTime) throws IOException {
        if (target == null) return;

        double error = Math.abs(target - current);

        // 1. Check if Target Reached (Clear Timers)
        if (isTargetReached(ctx, error)) {
            return;
        }

        // 2. Calculate appropriate delay based on severity
        long checkDelay = calculateStuckCheckDelay(current, error);

        // 3. Check progress and update timer accordingly
        boolean movingTowardsTarget = isMovingTowardsTarget(target, old, error);
        updateStuckTimer(ctx, eventTime, checkDelay, movingTowardsTarget);
    }

    private boolean isTargetReached(Context ctx, double error) throws IOException {
        if (error < config.tempChangeThreshold) {
            Long timer = registeredStuckTimerState.value();
            if (timer != null) {
                ctx.timerService().deleteEventTimeTimer(timer);
                registeredStuckTimerState.clear();
                stuckRepeatCountState.clear();
            }
            return true;
        }
        return false;
    }

    private long calculateStuckCheckDelay(double current, double error) {
        boolean isExtreme = current < (config.absMinTemp + 10) || current > (config.absMaxTemp - 10);
        boolean isLargeDeviation = error > config.largeDeviationThreshold;

        if (isExtreme) {
            return 60000L;
        } else if (isLargeDeviation) {
            return config.timeoutStuckHigh;
        } else {
            return config.timeoutStuckLow;
        }
    }

    private boolean isMovingTowardsTarget(Double target, Double old, double currentError) {
        if (old == null) return false;
        double oldError = Math.abs(target - old);
        return currentError < oldError - config.tempChangeThreshold;
    }

    private void updateStuckTimer(Context ctx, long eventTime, long checkDelay, boolean movingTowardsTarget) throws IOException {
        Long currentTimer = registeredStuckTimerState.value();

        if (movingTowardsTarget) {
            if (currentTimer != null) {
                ctx.timerService().deleteEventTimeTimer(currentTimer);
            }
            stuckRepeatCountState.clear();
            registerStuckTimer(ctx, eventTime + checkDelay);
        } else if (currentTimer == null) {
            registerStuckTimer(ctx, eventTime + checkDelay);
        }
    }

    private void registerStuckTimer(Context ctx, long timestamp) throws IOException {
        ctx.timerService().registerEventTimeTimer(timestamp);
        registeredStuckTimerState.update(timestamp);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<TelemetryEvent> out) throws Exception {
        TimeDomain domain = ctx.timeDomain();

        if (domain == TimeDomain.EVENT_TIME) {
            Long stuckTimer = registeredStuckTimerState.value();
            if (stuckTimer != null && timestamp == stuckTimer) {
                Double current = lastTempState.value();
                Double target = targetTempState.value();

                Integer repeatCount = stuckRepeatCountState.value();
                if (repeatCount == null) repeatCount = 0;
                repeatCount++;

                String msg = String.format("Temperature stuck! Current: %.1f, Target: %.1f (Alert #%d)", current, target, repeatCount);
                sendAlert(ctx, ctx.getCurrentKey(), AlertType.STUCK, msg, current, stuckAlerts);
                LOG.warn("Device {} {}", ctx.getCurrentKey(), msg);

                long backoffMultiplier = (long) Math.pow(2, Math.min(repeatCount, 4));
                long nextDelay = config.timeoutStuckHigh * backoffMultiplier;

                registerStuckTimer(ctx, timestamp + nextDelay);
                stuckRepeatCountState.update(repeatCount);
            }
        }
        else if (domain == TimeDomain.PROCESSING_TIME) {
            Long hbTimer = registeredHeartbeatTimerState.value();
            if (hbTimer != null && timestamp == hbTimer) {
                Long lastProcTime = lastHeartbeatProcessingTimeState.value();
                if (lastProcTime != null && (timestamp >= lastProcTime + config.timeoutHeartbeat)) {
                    sendAlert(ctx, ctx.getCurrentKey(), AlertType.OFFLINE,
                            "No data received from device (Timeout).", null, offlineAlerts);
                    LOG.info("Device {} is OFFLINE", ctx.getCurrentKey());
                }
                registeredHeartbeatTimerState.clear();
            }
        }
    }

    private void sendAlert(Context ctx, String deviceId, AlertType type, String msg, Double value, Counter metric) {
        try {
            AlertEvent alert = AlertEvent.builder()
                    .alertId(UUID.randomUUID().toString())
                    .deviceId(deviceId)
                    .type(type)
                    .message(msg)
                    .value(value)
                    .timestamp(Instant.now())
                    .build();

            String json = objectMapper.writeValueAsString(alert);
            ctx.output(alertsTag, json);
            if (metric != null) metric.inc();
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize alert for device {}", deviceId, e);
        }
    }

    private void sendAlert(OnTimerContext ctx, String deviceId, AlertType type, String msg, Double value, Counter metric) {
        try {
            AlertEvent alert = AlertEvent.builder()
                    .alertId(UUID.randomUUID().toString())
                    .deviceId(deviceId)
                    .type(type)
                    .message(msg)
                    .value(value)
                    .timestamp(Instant.now())
                    .build();

            String json = objectMapper.writeValueAsString(alert);
            ctx.output(alertsTag, json);
            if (metric != null) metric.inc();
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize alert for device {}", deviceId, e);
        }
    }
}