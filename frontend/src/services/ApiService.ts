import axios from 'axios';
import KeycloakService from './KeycloakService';
import { toast } from 'react-toastify';

import type { Device, HistoryPoint, DashboardStats, Alert, Page, ApiError } from '../types';

const apiClient = axios.create({
    baseURL: import.meta.env.VITE_BACKEND_API_URL,
    timeout: 10000,
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
    (response) => response,
    (error) => {
        const apiError = error.response?.data as ApiError;
        const status = error.response?.status;

        // TraceID für das Debugging in der Konsole loggen
        if (apiError?.traceId) {
            console.error(`[Backend Error] TraceID: ${apiError.traceId}`, apiError);
        }

        // Netzwerkfehler oder Timeout
        if (!error.response) {
            if (error.code === 'ECONNABORTED') {
                toast.error("Zeitüberschreitung: Der Server antwortet nicht rechtzeitig.");
            } else {
                toast.error("Netzwerkfehler: Bitte prüfen Sie Ihre Internetverbindung.");
            }
            return Promise.reject(error);
        }

        // Auth-Fehler (401)
        if (status === 401) {
            KeycloakService.logout();
            return Promise.reject(apiError);
        }

        // Business-Logik Fehler (errorCode)
        switch (apiError?.errorCode) {
            case "DEVICE_NOT_FOUND":
                toast.error("Gerät wurde nicht gefunden.");
                break;
            case "ACCESS_DENIED":
                toast.error("Zugriff verweigert: Sie besitzen dieses Gerät nicht.");
                break;
            case "INVALID_TEMPERATURE_RANGE":
                const details = apiError.details;
                toast.warning(`Temperatur ungültig! Bereich: ${details.minAllowed}°C bis ${details.maxAllowed}°C`);
                break;
            case "MQTT_BROKER_UNAVAILABLE":
                toast.error("Gerät antwortet nicht (MQTT-Verbindung unterbrochen).");
                break;
            case "VALIDATION_FAILED":
                toast.warning("Bitte überprüfen Sie die Formulardaten.");
                break;
            case "DUPLICATE_ALERT":
                console.warn("Alert Duplicate ignored");
                break;
            default:
                if (status === 503 || status === 504) {
                    toast.error("Server vorübergehend überlastet. Bitte warten.");
                } else if (status >= 500) {
                    toast.error(`Kritischer Fehler. Trace-ID: ${apiError?.traceId || 'N/A'}`);
                } else {
                    toast.error(apiError?.message || "Ein unbekannter Fehler ist aufgetreten.");
                }
        }

        return Promise.reject(apiError || error);
    }
);

export const getDevices = async () => (await apiClient.get<Device[]>('/devices')).data;

export const getDeviceById = async (id: string) => (await apiClient.get<Device>(`/devices/${id}`)).data;

export const updateDeviceName = async (id: string, name: string) => 
    (await apiClient.patch<Device>(`/devices/${id}`, { name })).data;

export const deleteDevice = async (id: string) => await apiClient.delete(`/devices/${id}`);

export const sendTemperatureCommand = async (id: string, value: number) => 
    await apiClient.post(`/devices/${id}/command/temperature`, { value });
    
export const generateClaimCode = async () => 
    (await apiClient.post<{ claimCode: string }>('/devices/generate-claim-code')).data;

export const getDeviceHistory = async (id: string, range = '-1h'): Promise<HistoryPoint[]> => {
    try {
        const res = await apiClient.get<HistoryPoint[]>(`/devices/${id}/telemetry/history`, { params: { range } });
        return res.data;
    } catch { return []; }
};

// --- ALERT METHODS ---

export const getAlerts = async (page = 0, size = 10, unreadOnly = false): Promise<Page<Alert>> => {
    try {
        const response = await apiClient.get<Page<Alert>>('/alerts', {
            params: { page, size, unreadOnly }
        });
        return response.data;
    } catch (error) {
        return {
            content: [],
            totalElements: 0,
            totalPages: 0,
            number: page,
            size: size
        };
    }
};

export const markAlertAsRead = async (id: string) => await apiClient.patch(`/alerts/${id}/read`);
export const markAllAlertsAsRead = async () => await apiClient.patch('/alerts/read-all');
export const deleteAlert = async (id: string) => await apiClient.delete(`/alerts/${id}`);

// --- DASHBOARD METHODS ---

export const getDashboardStats = async (): Promise<DashboardStats> => {
    try {
        const response = await apiClient.get<DashboardStats>('/dashboard');
        return response.data;
    } catch (error) {
        // Return default structure
        return { popularDevices: [], recentAlerts: [] };
    }
};

export const trackDeviceView = async (id: string) => await apiClient.post(`/dashboard/track/${id}`);

export default apiClient;