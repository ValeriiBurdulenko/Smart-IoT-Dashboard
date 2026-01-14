import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
    Grid, Paper, Typography, Box, Button, CircularProgress,
    useTheme
} from '@mui/material';
import DevicesIcon from '@mui/icons-material/Devices';

import { getDeviceHistory, getDeviceById } from '../services/ApiService';
import type { HistoryPoint } from '../services/ApiService';
import type { Device } from '../types';
import TemperatureChart from '../components/TemperatureChart';
import WebSocketService from '../services/WebSocketService';

// ━━━ Constants ━━━
const HISTORY_LIMIT = 200;
const DATA_STALE_TIMEOUT = 10000; // 10 seconds
const STORAGE_KEY = 'last_device_id';

const DashboardPage: React.FC = () => {
    const theme = useTheme();
    const [lastDevice, setLastDevice] = useState<Device | null>(null);
    const [chartData, setChartData] = useState<HistoryPoint[]>([]);
    const [loading, setLoading] = useState(true);
    const subscriptionRef = useRef<any>(null);
    const staleTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // ━━━ Button Style ━━━
    const primaryButtonStyle = {
        color: '#fff',
        backgroundColor: theme.palette.primary.main,
        '&:hover': {
            backgroundColor: theme.palette.primary.dark,
            color: '#fff',
            boxShadow: '0 4px 12px rgba(0,0,0,0.15)'
        }
    };

    // ━━━ Safe localStorage access ━━━
    const getStoredDeviceId = useCallback((): string | null => {
        try {
            return typeof window !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null;
        } catch (e) {
            console.warn('localStorage unavailable:', e);
            return null;
        }
    }, []);

    const setStoredDeviceId = useCallback((id: string): void => {
        try {
            if (typeof window !== 'undefined') {
                localStorage.setItem(STORAGE_KEY, id);
            }
        } catch (e) {
            console.warn('localStorage unavailable:', e);
        }
    }, []);

    // ━━━ Initial Load ━━━
    useEffect(() => {
        const loadDashboard = async () => {
            setLoading(true);
            try {
                const localId = getStoredDeviceId();

                if (localId) {
                    try {
                        const deviceData = await getDeviceById(localId);
                        setLastDevice(deviceData);

                        const history = await getDeviceHistory(localId);
                        setChartData(history);
                    } catch (error) {
                        console.warn(`Device ${localId} not found or deleted:`, error);
                        // Clear invalid device ID
                        try {
                            if (typeof window !== 'undefined') {
                                localStorage.removeItem(STORAGE_KEY);
                            }
                        } catch (e) {
                            console.warn('Could not clear localStorage:', e);
                        }
                    }
                }
            } catch (error) {
                console.error('Dashboard load failed:', error);
            } finally {
                setLoading(false);
            }
        };

        loadDashboard();
    }, [getStoredDeviceId]);

    // ━━━ WebSocket Subscription ━━━
    useEffect(() => {
        if (!lastDevice?.deviceId) return;

        // Clear previous timeout
        if (staleTimeoutRef.current) {
            clearTimeout(staleTimeoutRef.current);
        }

        const subscription = WebSocketService.subscribeToDevice(
            lastDevice.deviceId,
            (telemetry) => {
                const newPoint: HistoryPoint = {
                    timestamp: telemetry.timestamp,
                    temperature: telemetry.data.currentTemperature
                };

                setChartData((prev) => {
                    const newData = [...prev, newPoint];
                    return newData.length > HISTORY_LIMIT
                        ? newData.slice(newData.length - HISTORY_LIMIT)
                        : newData;
                });

                // Reset stale timer
                if (staleTimeoutRef.current) {
                    clearTimeout(staleTimeoutRef.current);
                }
            }
        );

        subscriptionRef.current = subscription;

        return () => {
            subscriptionRef.current?.unsubscribe();
            if (staleTimeoutRef.current) {
                clearTimeout(staleTimeoutRef.current);
            }
        };
    }, [lastDevice?.deviceId]);

    if (loading) {
        return (
            <Box p={4} display="flex" justifyContent="center">
                <CircularProgress />
            </Box>
        );
    }

    return (
        <Box sx={{ width: '100%' }}>
            <Grid container spacing={3}>
                {/* Main Chart */}
                <Grid size={{ xs: 12 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            p: 3,
                            border: `1px solid ${theme.palette.divider}`,
                            borderRadius: 2
                        }}
                    >
                        <Box
                            sx={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                mb: 2
                            }}
                        >
                            <Box>
                                <Typography variant="overline" color="text.secondary">
                                    Zuletzt angesehen
                                </Typography>
                                <Typography variant="h5" fontWeight="bold">
                                    {lastDevice ? lastDevice.name || lastDevice.deviceId : 'Willkommen'}
                                </Typography>
                            </Box>

                            {lastDevice && (
                                <Button
                                    component={RouterLink}
                                    to={`/devices/${lastDevice.deviceId}`}
                                    variant="contained"
                                    sx={primaryButtonStyle}
                                >
                                    Details öffnen
                                </Button>
                            )}
                        </Box>

                        {lastDevice ? (
                            <Box sx={{ flexGrow: 1, mt: 2 }}>
                                <TemperatureChart
                                    data={chartData}
                                    loading={loading}
                                    height={350}
                                />
                            </Box>
                        ) : (
                            <Box
                                sx={{
                                    height: 200,
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    flexDirection: 'column',
                                    bgcolor: 'background.default',
                                    borderRadius: 2
                                }}
                            >
                                <DevicesIcon
                                    sx={{
                                        fontSize: 60,
                                        color: 'text.disabled',
                                        mb: 2
                                    }}
                                />
                                <Typography color="text.secondary" gutterBottom>
                                    Sie haben noch keine Geräte angesehen.
                                </Typography>
                                <Button
                                    variant="contained"
                                    component={RouterLink}
                                    to="/devices"
                                    sx={{ mt: 2, ...primaryButtonStyle }}
                                >
                                    Zu den Geräten
                                </Button>
                            </Box>
                        )}
                    </Paper>
                </Grid>

                {/* Placeholder Cards */}
                <Grid size={{ xs: 12, md: 4 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            border: `1px solid ${theme.palette.divider}`,
                            p: 2,
                            height: 240
                        }}
                    >
                        <Typography variant="h6">Gerätestatus</Typography>
                    </Paper>
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            border: `1px solid ${theme.palette.divider}`,
                            p: 2,
                            height: 240
                        }}
                    >
                        <Typography variant="h6">Aktuelle Alarme</Typography>
                    </Paper>
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            border: `1px solid ${theme.palette.divider}`,
                            p: 2,
                            height: 240
                        }}
                    >
                        <Typography variant="h6">Luftfeuchtigkeit</Typography>
                    </Paper>
                </Grid>
            </Grid>
        </Box>
    );
};

export default DashboardPage;