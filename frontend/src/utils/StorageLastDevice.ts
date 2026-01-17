export const STORAGE_KEYS = {
    LAST_DEVICE_ID: 'last_device_id',
} as const;

export const getStoredDeviceId = (): string | null => {
    try {
        return typeof window !== 'undefined' ? localStorage.getItem(STORAGE_KEYS.LAST_DEVICE_ID) : null;
    } catch (e) {
        console.warn('localStorage unavailable:', e);
        return null;
    }
};

export const setStoredDeviceId = (id: string): void => {
    try {
        if (typeof window !== 'undefined') {
            localStorage.setItem(STORAGE_KEYS.LAST_DEVICE_ID, id);
        }
    } catch (e) {
        console.warn('localStorage unavailable:', e);
    }
};

export const clearStoredDeviceId = (): void => {
    try {
        if (typeof window !== 'undefined') {
            localStorage.removeItem(STORAGE_KEYS.LAST_DEVICE_ID);
        }
    } catch (e) {
        console.warn('localStorage unavailable:', e);
    }
};