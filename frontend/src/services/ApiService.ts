import axios from 'axios';
import KeycloakService from './KeycloakService';


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

export default apiClient;