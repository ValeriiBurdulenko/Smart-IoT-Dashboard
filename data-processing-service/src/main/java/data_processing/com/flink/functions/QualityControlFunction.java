package data_processing.com.flink.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import data_processing.com.flink.DataProcessingJob.ParsedEvent;
import data_processing.com.flink.model.ProcessingConfig;
import data_processing.com.flink.model.AlertEvent;
import data_processing.com.flink.model.AlertType;
import data_processing.com.flink.model.TelemetryEvent;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Quality Control Function.
 * V4.1 REFACTOR: Replaced Generic Exceptions with Specific Exceptions (IOException, JsonProcessingException).
 */
public class QualityControlFunction extends KeyedProcessFunction<String, ParsedEvent, TelemetryEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(QualityControlFunction.class);

    private final OutputTag<String> dlqTag;
    private final OutputTag<String> alertsTag;
    private final ProcessingConfig config;
    private transient ObjectMapper objectMapper;

    private transient ValueState<Long> lastEventTimeState;
    private transient ValueState<Integer> spamCounterState;
    private transient ValueState<Long> lastSpamAlertTimeState;
    private transient ValueState<Long> spamResetTimerState;

    private transient ValueState<Integer> badDataCounterState;
    private transient ValueState<Long> windowStartTimeState;

    private transient Counter alertCounter;
    private transient Counter dropCounter;
    private transient Counter futureDropCounter;

    private static final long MAX_FUTURE_DRIFT_MS = 60_000L;

    public QualityControlFunction(ProcessingConfig config, OutputTag<String> dlqTag, OutputTag<String> alertsTag) {
        this.config = config;
        this.dlqTag = dlqTag;
        this.alertsTag = alertsTag;
    }

    @Override
    public void open(Configuration parameters) {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        lastEventTimeState = getRuntimeContext().getState(new ValueStateDescriptor<>("lastEventTime", Long.class));
        spamCounterState = getRuntimeContext().getState(new ValueStateDescriptor<>("spamCounter", Integer.class));
        lastSpamAlertTimeState = getRuntimeContext().getState(new ValueStateDescriptor<>("lastSpamAlert", Long.class));
        spamResetTimerState = getRuntimeContext().getState(new ValueStateDescriptor<>("spamResetTimer", Long.class));

        badDataCounterState = getRuntimeContext().getState(new ValueStateDescriptor<>("badDataCounter", Integer.class));
        windowStartTimeState = getRuntimeContext().getState(new ValueStateDescriptor<>("windowStart", Long.class));

        alertCounter = getRuntimeContext().getMetricGroup().counter("alerts_qc_sent");
        dropCounter = getRuntimeContext().getMetricGroup().counter("events_dropped");
        futureDropCounter = getRuntimeContext().getMetricGroup().counter("events_future_dropped");
    }

    @Override
    public void processElement(ParsedEvent value, Context ctx, Collector<TelemetryEvent> out) throws Exception {
        long serverTime = ctx.timerService().currentProcessingTime();
        long eventTime;

        if (value.getEvent() != null && value.getEvent().getTimestamp() != null) {
            eventTime = value.getEvent().getTimestamp().toEpochMilli();
        } else {
            eventTime = serverTime;
        }

        // Security Check: Future Drift
        if (eventTime > serverTime + MAX_FUTURE_DRIFT_MS) {
            if (value.getEvent() != null) {
                LOG.warn("Dropped FUTURE event. EventTime: {}, ServerTime: {}", eventTime, serverTime);
            }
            futureDropCounter.inc();
            ctx.output(dlqTag, "FUTURE_DRIFT_EXCEEDED: " + value.getRawJson());
            return;
        }

        // Rule 1: Bad Data
        if (value.getEvent() == null) {
            handleInvalidData(value, ctx, eventTime);
            return;
        }

        // Rule 2: Rate Limiting
        if (isSpamOrRateLimited(ctx, eventTime, serverTime, value.getDeviceId())) {
            dropCounter.inc();
            return;
        }

        out.collect(value.getEvent());
    }

    private boolean isSpamOrRateLimited(Context ctx, long currentEventTime, long serverTime, String deviceId) throws IOException {
        Long lastEventTime = lastEventTimeState.value();

        // First event is always valid
        if (lastEventTime == null) {
            processValidEvent(ctx, currentEventTime);
            return false;
        }

        long diff = currentEventTime - lastEventTime;

        // Rate Limit Violation
        if (diff < config.rateLimitMs) {
            handleSpamViolation(ctx, serverTime, deviceId);
            return true;
        }

        // Valid Request
        processValidEvent(ctx, currentEventTime);
        return false;
    }

    private void handleSpamViolation(Context ctx, long serverTime, String deviceId) throws IOException {
        Integer spamCount = incrementSpamCounter();
        refreshSpamResetTimer(ctx, serverTime);

        if (spamCount > config.spamThresholdCount) {
            checkAndSendSpamAlert(ctx, serverTime, deviceId, spamCount);
        }
    }

    private void processValidEvent(Context ctx, long currentEventTime) throws IOException {
        lastEventTimeState.update(currentEventTime);
        spamCounterState.update(0);
        clearSpamResetTimer(ctx);
    }

    private Integer incrementSpamCounter() throws IOException {
        Integer spamCount = spamCounterState.value();
        if (spamCount == null) spamCount = 0;
        spamCount++;
        spamCounterState.update(spamCount);
        return spamCount;
    }

    private void refreshSpamResetTimer(Context ctx, long serverTime) throws IOException {
        Long existingResetTimer = spamResetTimerState.value();
        if (existingResetTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(existingResetTimer);
        }
        long resetTime = serverTime + 60_000L;
        ctx.timerService().registerProcessingTimeTimer(resetTime);
        spamResetTimerState.update(resetTime);
    }

    private void clearSpamResetTimer(Context ctx) throws IOException {
        Long resetTimer = spamResetTimerState.value();
        if (resetTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(resetTimer);
            spamResetTimerState.clear();
        }
    }

    private void checkAndSendSpamAlert(Context ctx, long serverTime, String deviceId, int spamCount) throws IOException {
        Long lastSpamAlert = lastSpamAlertTimeState.value();

        if (lastSpamAlert == null || (serverTime - lastSpamAlert > config.spamAlertCooldownMs)) {
            sendAlert(ctx, deviceId, AlertType.SECURITY_SPAM, "Excessive request rate.", (double) spamCount);
            lastSpamAlertTimeState.update(serverTime);
        }
    }

    private void handleInvalidData(ParsedEvent value, Context ctx, long currentEventTime) throws IOException {
        ctx.output(dlqTag, value.getRawJson());

        Integer badCount = badDataCounterState.value();
        if (badCount == null) badCount = 0;
        badCount++;

        Long winStart = windowStartTimeState.value();
        if (winStart == null || (currentEventTime - winStart > config.badDataWindowMs)) {
            badCount = 1;
            windowStartTimeState.update(currentEventTime);
        }

        badDataCounterState.update(badCount);

        if (badCount >= config.badDataThreshold && badCount == config.badDataThreshold) {
                sendAlert(ctx, value.getDeviceId(), AlertType.MALFUNCTION, "High rate of invalid data detected.", (double) badCount);
                LOG.warn("Device {} triggered MALFUNCTION alert.", value.getDeviceId());
            }

    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<TelemetryEvent> out) throws Exception {
        Long resetTimer = spamResetTimerState.value();
        if (resetTimer != null && timestamp == resetTimer) {
            spamCounterState.clear();
            spamResetTimerState.clear();
        }
    }

    private void sendAlert(Context ctx, String deviceId, AlertType type, String msg, Double value) {
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
            alertCounter.inc();
        } catch (JsonProcessingException e) {
            LOG.error("Error serializing alert", e);
        }
    }
}