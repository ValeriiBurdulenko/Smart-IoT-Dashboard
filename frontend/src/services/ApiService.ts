import axios from 'axios';
import KeycloakService from './KeycloakService';

import type { Device, HistoryPoint, DashboardStats } from '../types';

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

export const getDevices = async (): Promise<Device[]> => {
    try {
        const response = await apiClient.get<Device[]>('/devices');
        return response.data;
    } catch (error) {
        console.error("Failed to fetch devices:", error);
        throw error;
    }
};

export const getDeviceById = async (deviceId: string): Promise<Device> => {
    try {
        const response = await apiClient.get<Device>(`/devices/${deviceId}`);
        return response.data;
    } catch (error) {
        console.error("Failed to fetch device:", error);
        throw error;
    }
};

export const updateDeviceName = async (deviceId: string, name: string): Promise<Device> => {
    try {
        const response = await apiClient.patch<Device>(`/devices/${deviceId}`, { name });
        return response.data;
    } catch (error) {
        console.error("Failed to update device name:", error);
        throw error;
    }
};

export const deleteDevice = async (externalId: string): Promise<void> => {
    try {
        await apiClient.delete(`/devices/${externalId}`);
    } catch (error) {
        console.error("Failed to delete device:", error);
        throw error;
    }
};

export const generateClaimCode = async (): Promise<{ claimCode: string }> => {
    try {
        const response = await apiClient.post<{ claimCode: string }>('/devices/generate-claim-code');
        return response.data;
    } catch (error) {
        console.error("Failed to generate claim code:", error);
        throw error;
    }
};

export const sendTemperatureCommand = async (deviceId: string, value: number): Promise<void> => {
    try {
        await apiClient.post<void>(`/devices/${deviceId}/command/temperature`, { value });
    } catch (error) {
        console.error("Failed to send temperature command:", error);
        throw error;
    }
};

export const getDeviceHistory = async (deviceId: string, range = '-1h'): Promise<HistoryPoint[]> => {
    try {
        const response = await apiClient.get<HistoryPoint[]>(`/devices/${deviceId}/telemetry/history`, {
            params: { range }
        });
        return response.data;
    } catch (error) {
        console.error("Failed to fetch device history:", error);
        return [];  // Return empty array
    }
};

export const getDashboardStats = async (): Promise<DashboardStats> => {
    try {
        const response = await apiClient.get<DashboardStats>('/dashboard');
        return response.data;
    } catch (error) {
        console.error("Failed to fetch dashboard stats:", error);
        // Return default structure
        return { popularDevices: [] };
    }
};

export const trackDeviceView = async (deviceId: string): Promise<void> => {
    try {
        await apiClient.post<void>(`/dashboard/track/${deviceId}`);
    } catch (error) {
        console.error("Failed to track device view:", error);
    }
};

export default apiClient;