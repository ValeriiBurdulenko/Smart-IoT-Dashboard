package data_processing.com.flink;

import com.influxdb.client.DeleteApi;
import data_processing.com.flink.model.SensorData;
import data_processing.com.flink.model.TelemetryEvent;
import data_processing.com.flink.model.DeviceDeleteEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class DataProcessingJob {

    // --- Schlüssel für die Konfigurationsdatei ---
    private static final String KAFKA_BROKERS_KEY = "kafka.brokers";
    private static final String KAFKA_SOURCE_TOPIC_KEY = "kafka.topic.raw";
    private static final String KAFKA_SINK_TOPIC_KEY = "kafka.topic.processed";
    private static final String KAFKA_GROUP_ID_KEY_PROCESSED = "kafka.group.id.processed";
    private static final String INFLUXDB_URL_KEY = "influxdb.url";
    private static final String INFLUXDB_TOKEN_KEY = "influxdb.token";
    private static final String INFLUXDB_ORG_KEY = "influxdb.org";
    private static final String INFLUXDB_BUCKET_KEY = "influxdb.bucket";
    private static final String KAFKA_DELETE_TOPIC_KEY = "kafka.topic.deletions";
    private static final String KAFKA_GROUP_ID_KEY_DELETIONS = "kafka.group.id.deletions";

    public static void main(String[] args) throws Exception {

        // --- 1. Konfiguration laden ---
        ParameterTool params;
        try (InputStream propertiesStream = DataProcessingJob.class.getClassLoader().getResourceAsStream("flink.properties")) {
            if (propertiesStream == null) {
                System.err.println("!!! 'flink.properties' not found in resources!");
                return;
            }
            // Lädt aus Datei UND überschreibt mit --arg Argumenten, falls vorhanden
            params = ParameterTool.fromPropertiesFile(propertiesStream)
                    .mergeWith(ParameterTool.fromArgs(args));
        } catch (Exception e) {
            System.err.println("Failed to load properties: " + e.getMessage());
            return;
        }

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // --- 2. Quelle: Kafka (rohe Telemetrie) ---
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(params.getRequired(KAFKA_BROKERS_KEY))
                .setTopics(params.getRequired(KAFKA_SOURCE_TOPIC_KEY))
                .setGroupId(params.get(KAFKA_GROUP_ID_KEY_PROCESSED, "flink-processor-group"))
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> kafkaStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");

        // --- 3. Transformation: JSON -> POJO -> Validierung ---
        DataStream<TelemetryEvent> validatedStream = kafkaStream
                .map(new JsonToPojoMapper())
                .filter(event -> event != null && event.isValid())
                .name("Validate & Parse");

        // --- 4. Sink 1: InfluxDB (für Grafiken) ---
        AsyncDataStream
                .unorderedWait(
                        validatedStream,
                        new InfluxDbSinkFunction(params),
                        2000, TimeUnit.MILLISECONDS, 100
                )
                .name("InfluxDB Sink");

        // --- 5. Sink 2: Kafka (verarbeitete Daten für WebSockets) ---
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(params.getRequired(KAFKA_BROKERS_KEY))
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(params.getRequired(KAFKA_SINK_TOPIC_KEY))
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        validatedStream
                .map(new PojoToJsonMapper())
                .sinkTo(kafkaSink).name("Processed Kafka Sink");

        // --- CONVEYOR 2: PROCESSING DELETION REQUESTS ---

        // 2.1. Source: Reading the deletion topic
        KafkaSource<String> deleteSource = KafkaSource.<String>builder()
                .setBootstrapServers(params.getRequired(KAFKA_BROKERS_KEY))
                .setTopics(params.getRequired(KAFKA_DELETE_TOPIC_KEY))
                .setGroupId(params.get(KAFKA_GROUP_ID_KEY_DELETIONS,"flink-data-purger-group"))
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> deleteJsonStream = env.fromSource(deleteSource, WatermarkStrategy.noWatermarks(), "Deletion Source");

        // 2.2. Transformation: JSON -> POJO -> Filtering
        DataStream<DeviceDeleteEvent> deleteEventStream = deleteJsonStream
                .map(new JsonToDeleteEventMapper())
                .filter(event -> event != null && "PURGE".equals(event.getAction()))
                .name("Parse & Validate Deletion Event");

        // 2.3. Sink 3: Receiver for deletion from InfluxDB
        AsyncDataStream
                .unorderedWait(
                        deleteEventStream,
                        new InfluxDbDeleteSink(params),
                        60000, TimeUnit.MILLISECONDS, 10
                )
                .name("InfluxDB Delete Sink");


        // --- Start all conveyors ---
        env.execute("IoT Data Pipeline (Processing & Deletion)");
    }

    // --- Hilfsklassen für Flink ---

    /**
     * Konvertiert JSON-String in TelemetryEvent POJO
     */
    public static class JsonToPojoMapper extends RichMapFunction<String, TelemetryEvent> {
        private transient ObjectMapper objectMapper;

        @Override
        public void open(Configuration parameters) {
            objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Override
        public TelemetryEvent map(String value) {
            try {
                return objectMapper.readValue(value, TelemetryEvent.class);
            } catch (Exception e) {
                System.err.println("Failed to parse JSON: " + value);
                return null; // Wird später von .filter() entfernt
            }
        }
    }

    /**
     * Konvertiert TelemetryEvent POJO zurück in JSON-String
     */
    public static class PojoToJsonMapper extends RichMapFunction<TelemetryEvent, String> {
        private transient ObjectMapper objectMapper;

        @Override
        public void open(Configuration parameters) {
            objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Override
        public String map(TelemetryEvent value) throws Exception {
            return objectMapper.writeValueAsString(value);
        }
    }

    /**
     * Schreibt Telemetriedaten asynchron in InfluxDB
     */
    public static class InfluxDbSinkFunction extends RichAsyncFunction<TelemetryEvent, Void> {
        private transient InfluxDBClient influxDBClient;
        private transient WriteApiBlocking writeApi;

        private final ParameterTool params;
        private String influxOrg;
        private String influxBucket;

        public InfluxDbSinkFunction(ParameterTool params) {
            this.params = params;
        }

        @Override
        public void open(Configuration parameters) {
            // Liest Parameter
            String influxUrl = params.getRequired(INFLUXDB_URL_KEY);
            String influxToken = params.getRequired(INFLUXDB_TOKEN_KEY);
            this.influxOrg = params.getRequired(INFLUXDB_ORG_KEY);
            this.influxBucket = params.getRequired(INFLUXDB_BUCKET_KEY);

            influxDBClient = InfluxDBClientFactory.create(influxUrl, influxToken.toCharArray(), influxOrg, influxBucket);
            writeApi = influxDBClient.getWriteApiBlocking();
        }

        @Override
        public void close() {
            if (influxDBClient != null) {
                influxDBClient.close();
            }
        }

        @Override
        public void asyncInvoke(TelemetryEvent event, ResultFuture<Void> resultFuture) {
            try {
                Point point = Point.measurement("telemetry")
                        .addTag("deviceId", event.getDeviceId())
                        .addField("currentTemperature", event.data.currentTemperature)
                        .addField("targetTemperature", event.data.targetTemperature)
                        .addField("heatingStatus", event.data.heatingStatus ? 1 : 0)
                        .time(event.getTimestamp(), WritePrecision.MS);

                writeApi.writePoint(point);
                resultFuture.complete(Collections.emptyList());
            } catch (Exception e) {
                System.err.println("Failed to write to InfluxDB: " + e.getMessage());
                resultFuture.completeExceptionally(e);
            }
        }

        @Override
        public void timeout(TelemetryEvent input, ResultFuture<Void> resultFuture) {
            resultFuture.completeExceptionally(new RuntimeException("InfluxDB write timeout for: " + input.getDeviceId()));
        }
    }

    /**
     * Converts a JSON delete string into a POJO DeviceDeleteEvent
     */
    public static class JsonToDeleteEventMapper extends RichMapFunction<String, DeviceDeleteEvent> {
        private transient ObjectMapper objectMapper;

        @Override
        public void open(Configuration parameters) {
            objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Override
        public DeviceDeleteEvent map(String value) {
            try {
                return objectMapper.readValue(value, DeviceDeleteEvent.class);
            } catch (Exception e) {
                System.err.println("Failed to parse delete event: " + value);
                return null;
            }
        }
    }

    // --- NEW SINK CLASS FOR REMOVAL ---
    public static class InfluxDbDeleteSink extends RichAsyncFunction<DeviceDeleteEvent, Void> {

        private transient InfluxDBClient influxDBClient;
        private transient DeleteApi deleteApi;

        private final ParameterTool params;
        private String influxOrg;
        private String influxBucket;

        public InfluxDbDeleteSink(ParameterTool params) {
            this.params = params;
        }

        @Override
        public void open(Configuration parameters) {
            String influxUrl = params.getRequired(INFLUXDB_URL_KEY);
            String influxToken = params.getRequired(INFLUXDB_TOKEN_KEY);
            this.influxOrg = params.getRequired(INFLUXDB_ORG_KEY);
            this.influxBucket = params.getRequired(INFLUXDB_BUCKET_KEY);

            influxDBClient = InfluxDBClientFactory.create(influxUrl, influxToken.toCharArray(), influxOrg, influxBucket);
            deleteApi = influxDBClient.getDeleteApi();
        }

        @Override
        public void close() {
            if (influxDBClient != null) {
                influxDBClient.close();
            }
        }

        @Override
        public void asyncInvoke(DeviceDeleteEvent event, ResultFuture<Void> resultFuture) {
            String deviceId = event.getDeviceId();
            try {
                System.out.println("!!! [PURGE JOB] Received command to delete InfluxDB data for: " + deviceId);

                // 1. Delete ALL data for this deviceId
                // (from 1970 to 2200 to cover everything)
                OffsetDateTime start = OffsetDateTime.parse("1970-01-01T00:00:00Z");
                OffsetDateTime stop = OffsetDateTime.parse("2200-01-01T00:00:00Z");

                // 2. Predicate: delete from “telemetry” where “deviceId” = X
                String predicate = String.format("_measurement=\"telemetry\" AND \"deviceId\"=\"%s\"", deviceId);

                // 3. Perform deletion
                deleteApi.delete(start, stop, predicate, influxBucket, influxOrg);

                System.out.println("!!! [PURGE JOB] InfluxDB data for " + deviceId + " has been successfully deleted.");
                resultFuture.complete(Collections.emptyList());
            } catch (Exception e) {
                System.err.println("!!! [PURGE JOB] Error while deleting InfluxDB data: " + e.getMessage());
                resultFuture.completeExceptionally(e);
            }
        }
    }
}