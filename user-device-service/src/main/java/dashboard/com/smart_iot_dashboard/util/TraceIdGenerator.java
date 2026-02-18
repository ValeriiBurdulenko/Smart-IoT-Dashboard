package dashboard.com.smart_iot_dashboard.util;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class TraceIdGenerator {

    private static final String TRACE_ID_KEY = "traceId";

    public String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    public void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public void clearTraceId() {
        MDC.remove(TRACE_ID_KEY);
    }
}