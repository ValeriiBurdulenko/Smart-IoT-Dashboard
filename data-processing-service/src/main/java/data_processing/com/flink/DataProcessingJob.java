package data_processing.com.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.influxdb.client.DeleteApi;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import data_processing.com.flink.functions.ComplexEventProcessor;
import data_processing.com.flink.functions.QualityControlFunction;
import data_processing.com.flink.model.DeviceDeleteEvent;
import data_processing.com.flink.model.ProcessingConfig;
import data_processing.com.flink.model.TelemetryEvent;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Production-ready Flink job for IoT data processing.
 * Clean version: Validation -> InfluxDB -> Kafka Processed.
 */
public class DataProcessingJob {

    private static final Logger LOG = LoggerFactory.getLogger(DataProcessingJob.class);

    // --- Config Keys ---
    private static final String KAFKA_BROKERS = "kafka.brokers";
    private static final String KAFKA_TOPIC_RAW = "kafka.topic.raw";
    private static final String KAFKA_TOPIC_PROCESSED = "kafka.topic.processed";
    private static final String KAFKA_TOPIC_DELETIONS = "kafka.topic.deletions";
    private static final String KAFKA_TOPIC_DLQ = "kafka.topic.dlq";
    private static final String KAFKA_TOPIC_ALERTS = "kafka.topic.alerts";

    private static final String KAFKA_GROUP_ID_TELEMETRY = "kafka.group.id.telemetry";
    private static final String KAFKA_GROUP_ID_DELETIONS = "kafka.group.id.deletions";

    private static final String INFLUX_URL = "influxdb.url";
    private static final String INFLUX_TOKEN = "influxdb.token";
    private static final String INFLUX_ORG = "influxdb.org";
    private static final String INFLUX_BUCKET = "influxdb.bucket";

    private static final String CHECKPOINT_STORAGE = "checkpoint.storage.path";
    private static final String KEY_SECURITY_PROTOCOL = "security.protocol";
    private static final String KEY_SASL_MECHANISM = "sasl.mechanism";
    private static final String KEY_SASL_JAAS_CONFIG = "sasl.jaas.config";

    //Validation Constant
    private static final String FIELD_DEVICE_ID = "deviceId";
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}$");
    // Side Output Tags
    public static final OutputTag<String> DLQ_TAG = new OutputTag<String>("dlq-events"){};
    public static final OutputTag<String> ALERTS_TAG = new OutputTag<String>("alert-events"){};

    public static void main(String[] args) throws Exception {

        ParameterTool params;
        try (InputStream input = DataProcessingJob.class.getClassLoader()
                .getResourceAsStream("flink.properties")) {
            if (input == null) throw new FileNotFoundException("'flink.properties' file not found in classpath");
            params = ParameterTool.fromPropertiesFile(input).mergeWith(ParameterTool.fromArgs(args));
        }

        LOG.info("=== Starting IoT Data Processing Job (Clean) ===");

        ProcessingConfig processingConfig = ProcessingConfig.fromParameters(params);

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();



        // --- 1. Reliability Settings ---
        env.getConfig().setGlobalJobParameters(params);

        env.getConfig().enableObjectReuse();

        // Checkpointing
        env.enableCheckpointing(60000);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setCheckpointStorage(params.get(CHECKPOINT_STORAGE, "file:///tmp/flink-checkpoints"));
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);

        // Restart Strategy
        env.setRestartStrategy(RestartStrategies.exponentialDelayRestart(
                Time.seconds(2), Time.seconds(60), 1.5, Time.minutes(10), 0.1
        ));

        if (params.has("flink.parallelism")) {
            env.setParallelism(params.getInt("flink.parallelism"));
        }

        Properties kafkaProps = createKafkaProperties(params);

        // ==========================================
        // PIPELINE 1: TELEMETRY (Validation -> DB -> Kafka)
        // ==========================================

        KafkaSource<String> telemetrySource = KafkaSource.<String>builder()
                .setProperties(kafkaProps)
                .setTopics(params.getRequired(KAFKA_TOPIC_RAW))
                .setGroupId(params.get(KAFKA_GROUP_ID_TELEMETRY, "flink-telemetry-group"))
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> rawTelemetryStream = env.fromSource(
                telemetrySource, WatermarkStrategy.noWatermarks(), "Telemetry Source"
        );

        // 1. Parse JSON first to get timestamp
        DataStream<ParsedEvent> parsedStream = rawTelemetryStream.map(new RawJsonMapper());

        // 2. Assign Watermarks based on Event Time
        DataStream<ParsedEvent> streamWithWatermarks = parsedStream.assignTimestampsAndWatermarks(
                WatermarkStrategy.<ParsedEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((SerializableTimestampAssigner<ParsedEvent>) (element, recordTimestamp) -> {
                            if (element.event != null && element.event.getTimestamp() != null) {
                                return element.event.getTimestamp().toEpochMilli();
                            }
                            return System.currentTimeMillis();
                        })
        );

        // Quality Control
        SingleOutputStreamOperator<TelemetryEvent> qualityCheckedStream = streamWithWatermarks
                .keyBy(ParsedEvent::getDeviceId)
                .process(new QualityControlFunction(processingConfig, DLQ_TAG, ALERTS_TAG))
                .name("Quality Control");

        DataStream<String> dlqStream = qualityCheckedStream.getSideOutput(DLQ_TAG);
        DataStream<String> qosAlerts = qualityCheckedStream.getSideOutput(ALERTS_TAG);

        // Complex Event Processing
        SingleOutputStreamOperator<TelemetryEvent> monitoredStream = qualityCheckedStream
                .keyBy(TelemetryEvent::getDeviceId)
                .process(new ComplexEventProcessor(processingConfig, ALERTS_TAG))
                .name("Complex Event Processor");

        DataStream<String> logicAlerts = monitoredStream.getSideOutput(ALERTS_TAG);

        // Sinks for Pipeline 1
        DataStream<String> allAlerts = qosAlerts.union(logicAlerts);
        allAlerts.sinkTo(createKafkaSink(params.get(KAFKA_TOPIC_ALERTS, "iot-telemetry-alerts"), kafkaProps)).name("Alerts Sink");
        dlqStream.sinkTo(createKafkaSink(params.get(KAFKA_TOPIC_DLQ, "iot-telemetry-dlq"), kafkaProps)).name("DLQ Sink");

        // Write to InfluxDB (Async, Non-blocking)
        AsyncDataStream.unorderedWait(
                monitoredStream,
                new InfluxDbSinkFunction(params),
                5000, TimeUnit.MILLISECONDS,
                20 // Concurrent requests
        ).name("InfluxDB Writer");

        // Forward to Processed Topic (for Frontend/WebSocket)
        KafkaSink<String> processedSink = KafkaSink.<String>builder()
                .setKafkaProducerConfig(kafkaProps)
                .setRecordSerializer(new KafkaRecordSerializationSchema<String>() {
                    @Nullable
                    @Override
                    public ProducerRecord<byte[], byte[]> serialize(String element, KafkaSinkContext context, Long timestamp) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode node = mapper.readTree(element);
                            String key = node.get(FIELD_DEVICE_ID).asText();

                            // [Image of Kafka Partition Keying]
                            return new ProducerRecord<>(
                                    params.getRequired(KAFKA_TOPIC_PROCESSED),
                                    null,
                                    timestamp,
                                    key.getBytes(StandardCharsets.UTF_8),
                                    element.getBytes(StandardCharsets.UTF_8)
                            );
                        } catch (Exception e) {
                            LOG.error("Failed to serialize for Kafka: {}", element, e);
                            return null;
                        }
                    }
                })
                .build();

        monitoredStream
                .map(new PojoToJsonMapper<>())
                .sinkTo(processedSink)
                .name("Kafka Processed Sink (Keyed)");

        // ==========================================
        // PIPELINE 2: DELETIONS (GDPR)
        // ==========================================

        KafkaSource<String> deletionSource = KafkaSource.<String>builder()
                .setProperties(kafkaProps)
                .setTopics(params.getRequired(KAFKA_TOPIC_DELETIONS))
                .setGroupId(params.get(KAFKA_GROUP_ID_DELETIONS, "flink-purger-group"))
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<DeviceDeleteEvent> deletionStream = env.fromSource(
                        deletionSource, WatermarkStrategy.noWatermarks(), "Deletion Source"
                )
                .map(new JsonToPojoMapper<>(DeviceDeleteEvent.class))
                .filter(e -> e != null && "PURGE".equals(e.getAction()));

        AsyncDataStream.unorderedWait(
                deletionStream,
                new InfluxDbDeleteSink(params),
                10000, TimeUnit.MILLISECONDS,
                5
        ).name("InfluxDB Purge");

        env.execute("IoT Data Processing Pipeline");
    }

    // --- Helpers ---
    private static Properties createKafkaProperties(ParameterTool params) {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", params.getRequired(KAFKA_BROKERS));

        if (params.has(KEY_SECURITY_PROTOCOL)) {
            props.setProperty(KEY_SECURITY_PROTOCOL, params.get(KEY_SECURITY_PROTOCOL));
        }

        if (params.has(KEY_SASL_MECHANISM)) {
            props.setProperty(KEY_SASL_MECHANISM, params.get(KEY_SASL_MECHANISM));
        }

        if (params.has(KEY_SASL_JAAS_CONFIG)) {
            props.setProperty(KEY_SASL_JAAS_CONFIG, params.get(KEY_SASL_JAAS_CONFIG));
        }
        return props;
    }

    private static KafkaSink<String> createKafkaSink(String topic, Properties props) {
        return KafkaSink.<String>builder()
                .setKafkaProducerConfig(props)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(topic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();
    }

    // --- Functions ---
    /**
     * Writes to InfluxDB asynchronously using a dedicated thread pool.
     * Closes resources correctly.
     */
    public static class InfluxDbSinkFunction extends RichAsyncFunction<TelemetryEvent, Void> {
        private transient InfluxDBClient client;
        private transient WriteApiBlocking writeApi;
        private transient ExecutorService executor;
        private final ParameterTool params;
        private transient Counter successCounter;
        private transient Counter failureCounter;

        public InfluxDbSinkFunction(ParameterTool params) { this.params = params; }

        @Override
        public void open(Configuration parameters) {
            String url = params.getRequired(INFLUX_URL);
            String token = params.getRequired(INFLUX_TOKEN);
            String org = params.getRequired(INFLUX_ORG);
            String bucket = params.getRequired(INFLUX_BUCKET);

            client = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
            writeApi = client.getWriteApiBlocking();
            executor = Executors.newFixedThreadPool(20);

            successCounter = getRuntimeContext().getMetricGroup().counter("influx_writes_success");
            failureCounter = getRuntimeContext().getMetricGroup().counter("influx_writes_failed");
        }

        @Override
        public void close() {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        LOG.warn("Executor did not terminate gracefully, forcing shutdown");
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            if (client != null) client.close();
        }

        @Override
        public void asyncInvoke(TelemetryEvent event, ResultFuture<Void> resultFuture) {
            CompletableFuture.runAsync(() -> writeWithRetry(event, resultFuture), executor);
        }

        private void writeWithRetry(TelemetryEvent event, ResultFuture<Void> resultFuture) {
            Point point = createTelemetryPoint(event);
            int maxRetries = 3;

            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    writeApi.writePoint(point);
                    successCounter.inc();
                    resultFuture.complete(Collections.emptyList());
                    return;
                } catch (Exception e) {
                    if (isLastAttempt(attempt, maxRetries)) {
                        failureCounter.inc();
                        LOG.error("Failed to write to InfluxDB after {} attempts", maxRetries, e);
                        resultFuture.completeExceptionally(e);
                    } else {
                        performBackoff(attempt);
                    }
                }
            }
        }

        private Point createTelemetryPoint(TelemetryEvent event) {
            int heatingStatus = Boolean.TRUE.equals(event.getData().getHeatingStatus()) ? 1 : 0;

            Point point = Point.measurement("telemetry")
                    .addTag(FIELD_DEVICE_ID, event.getDeviceId())
                    .addField("currentTemperature", event.getData().getCurrentTemperature())
                    .addField("heatingStatus", heatingStatus)
                    .time(event.getTimestamp(), WritePrecision.MS);

            if (event.getData().getTargetTemperature() != null) {
                point.addField("targetTemperature", event.getData().getTargetTemperature());
            }

            return point;
        }

        private void performBackoff(int attempt) {
            try {
                Thread.sleep(200L * (attempt + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private boolean isLastAttempt(int attempt, int maxRetries) {
            return attempt == maxRetries - 1;
        }
    }

    /**
     * Deletes data from InfluxDB upon request (GDPR).
     */
    public static class InfluxDbDeleteSink extends RichAsyncFunction<DeviceDeleteEvent, Void> {
        private transient InfluxDBClient client;
        private transient DeleteApi deleteApi;
        private transient ExecutorService executor;
        private final ParameterTool params;

        public InfluxDbDeleteSink(ParameterTool params) { this.params = params; }

        @Override public void open(Configuration c) {
            String url = params.getRequired(INFLUX_URL);
            String token = params.getRequired(INFLUX_TOKEN);
            String org = params.getRequired(INFLUX_ORG);
            String bucket = params.getRequired(INFLUX_BUCKET);

            client = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
            deleteApi = client.getDeleteApi();
            executor = Executors.newFixedThreadPool(5);
        }

        @Override public void close() {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            if(client!=null) client.close();
        }

        @Override public void asyncInvoke(DeviceDeleteEvent event, ResultFuture<Void> result) {
            CompletableFuture.runAsync(() -> {
                try {
                    String sanitizedId = sanitizeDeviceId(event.getDeviceId());
                    String predicate = String.format("_measurement=\"telemetry\" AND \"deviceId\"=\"%s\"", sanitizedId);
                    String bucket = params.getRequired(INFLUX_BUCKET);
                    String org = params.getRequired(INFLUX_ORG);
                    deleteApi.delete(OffsetDateTime.parse("1970-01-01T00:00:00Z"), OffsetDateTime.now(), predicate, bucket, org);
                    result.complete(Collections.emptyList());
                } catch (Exception e) {
                    result.completeExceptionally(e);
                }
            }, executor);
        }

        private String sanitizeDeviceId(String deviceId) {
            if (deviceId == null) {
                throw new IllegalArgumentException("Device ID cannot be null");
            }

            //Security Check: Null Byte Injection
            deviceId = deviceId.replace("\0", "");

            if (!UUID_PATTERN.matcher(deviceId).matches()) {
                throw new SecurityException("Potential SQL Injection detected: deviceId does not match expected UUID format");
            }

            return deviceId
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
        }
    }


    // --- Inner Classes --
    public static class ParsedEvent {
        private String deviceId;
        private TelemetryEvent event;
        private String rawJson;
        private boolean isStructurallyValid;

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public TelemetryEvent getEvent() { return event; }
        public void setEvent(TelemetryEvent event) { this.event = event; }

        public String getRawJson() { return rawJson; }
        public void setRawJson(String rawJson) { this.rawJson = rawJson; }

        public boolean isStructurallyValid() { return isStructurallyValid; }
        public void setStructurallyValid(boolean structurallyValid) { isStructurallyValid = structurallyValid; }
    }

    public static class RawJsonMapper extends RichMapFunction<String, ParsedEvent> {
        private transient ObjectMapper mapper;
        @Override public void open(Configuration c) { mapper = new ObjectMapper(); }
        @Override
        public ParsedEvent map(String value) {
            ParsedEvent pe = new ParsedEvent();
            pe.setRawJson(value);
            pe.setDeviceId("unknown");

            JsonNode node;
            try {
                node = mapper.readTree(value);
                pe.setStructurallyValid(true);
            } catch (Exception e) {
                pe.setStructurallyValid(false);
                return pe;
            }

            if (node.has(FIELD_DEVICE_ID)) {
                pe.setDeviceId(node.get(FIELD_DEVICE_ID).asText());
            }

            try {
                TelemetryEvent te = mapper.treeToValue(node, TelemetryEvent.class);
                if (te != null && te.isValid()) {
                    pe.setEvent(te);
                }
            } catch (Exception e) {
                pe.setEvent(null);
            }
            return pe;
        }
    }

    public static class JsonToPojoMapper<T> extends RichMapFunction<String, T> implements ResultTypeQueryable<T> {
        private final Class<T> targetClass;
        private transient ObjectMapper objectMapper;
        public JsonToPojoMapper(Class<T> targetClass) {
            this.targetClass = targetClass;
        }

        @Override public void open(Configuration c) {
            objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Override public T map(String value) {
            try {
                return objectMapper.readValue(value, targetClass);
            } catch (Exception e) {
                return null; }
        }

        @Override
        public TypeInformation<T> getProducedType() {
            return TypeInformation.of(targetClass);
        }
    }

    public static class PojoToJsonMapper<T> extends RichMapFunction<T, String> {
        private transient ObjectMapper objectMapper;
        @Override public void open(Configuration c) {
            objectMapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        }

        @Override public String map(T value) throws Exception {
            return objectMapper.writeValueAsString(value);
        }
    }
}