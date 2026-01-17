export interface Device {
    deviceId: string; // Dein 'externalId'
    name: string;
    location: string | null;
    active: boolean;
    deactivatedAt: string | null;
    targetTemperature: number;
  }

export interface HistoryPoint {
    timestamp: string;
    temperature: number;
}

// DTO for popular devices
export interface DeviceSummary {
  deviceId: string;
  name?: string;
}

export interface DashboardStats {
  popularDevices: DeviceSummary[];
}