export interface Device {
    deviceId: string; // Dein 'externalId'
    name: string;
    location: string | null;
    active: boolean;
    deactivatedAt: string | null;
    targetTemperature: number;
  }