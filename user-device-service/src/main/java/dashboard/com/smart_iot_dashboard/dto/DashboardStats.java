package dashboard.com.smart_iot_dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStats {
    // Возвращаем список легких DTO, а не полных сущностей
    private List<DeviceSummaryDTO> popularDevices;
}
