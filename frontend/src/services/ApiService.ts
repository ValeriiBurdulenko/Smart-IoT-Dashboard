import axios from 'axios';
import KeycloakService from './KeycloakService';

import type { Device } from '../types';

export interface HistoryPoint {
    timestamp: string;
    temperature: number;
}

const apiClient = axios.create({
    baseURL: import.meta.env.VITE_BACKEND_API_URL
});


apiClient.interceptors.request.use(
    (config) => {
        if (KeycloakService.isLoggedIn()) {
            const token = KeycloakService.getToken();
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);


apiClient.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        if (error.response && error.response.status === 401) {
            console.error("Unauthorized request. Logging out.");
            KeycloakService.logout();
        }
        return Promise.reject(error);
    }
);

export const getDevices = (): Promise<Device[]> => {
    return apiClient.get('/devices').then(res => res.data);
};

export const deleteDevice = (externalId: string): Promise<void> => {
    return apiClient.delete(`/devices/${externalId}`);
};

export const generateClaimCode = (): Promise<{ claimCode: string }> => {
    return apiClient.post('/devices/generate-claim-code').then(res => res.data);
};

export const getDeviceById = (deviceId: string): Promise<Device> => {
    return apiClient.get(`/devices/${deviceId}`).then(res => res.data);
};

export const updateDeviceName = (deviceId: string, name: string): Promise<Device> => {
    return apiClient.patch(`/devices/${deviceId}`, { name }).then(res => res.data);
};

export const sendTemperatureCommand = (deviceId: string, value: number): Promise<void> => {
    return apiClient.post(`/devices/${deviceId}/command/temperature`, { value });
};

export const getDeviceHistory = async (deviceId: string, range = '-1h'): Promise<HistoryPoint[]> => {
    try {
        const response = await apiClient.get<HistoryPoint[]>(`/devices/${deviceId}/telemetry/history`, {
            params: { range }
        });
        return response.data;
    } catch (error) {
        console.error("Failed to fetch history:", error);
        // Return an empty array so that the graph does not break the page with an error
        return []; 
    }
};

export default apiClient;