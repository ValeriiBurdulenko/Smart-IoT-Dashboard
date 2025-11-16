export interface Device {
    id: number; // Interner DB-Key
    deviceId: string; // Dein 'externalId'
    userId: string;
    name: string;
    location: string | null;
    active: boolean;
    deactivatedAt: string | null;
  }