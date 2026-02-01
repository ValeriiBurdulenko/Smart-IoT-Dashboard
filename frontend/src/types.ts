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
export interface DashboardDevice {
  deviceId: string;
  name?: string;
}

// DTO для алерта на дашборде (сокращенный)
export interface DashboardAlert {
  alertId: string;
  deviceId: string;
  type: AlertType;
  timestamp: number;
}

export interface DashboardStats {
  popularDevices: DashboardDevice[];
  recentAlerts: DashboardAlert[];
}


export type AlertType = 
    | 'MALFUNCTION' 
    | 'SECURITY_SPAM' 
    | 'CRITICAL_DIRECTION' 
    | 'STUCK' 
    | 'OFFLINE';

export interface Alert {
    alertId: string;
    deviceId: string;
    type: AlertType;
    message: string;
    value: number;
    timestamp: string;
    read: boolean;
}

export interface Page<T> {
    content: T[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
}

export interface ApiError {
  status: number;
  errorCode: string;
  message: string;
  details?: any;
  traceId: string;
  path: string;
}