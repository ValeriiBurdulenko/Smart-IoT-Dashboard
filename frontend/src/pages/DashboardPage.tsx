import React, { useState, useEffect, useRef } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
    Grid, Paper, Typography, Box, Button, CircularProgress,
    Card, CardActionArea, Stack, Chip, useTheme, Avatar
} from '@mui/material';
import DevicesIcon from '@mui/icons-material/Devices';

import { getDeviceHistory, getDeviceById, getDashboardStats } from '../services/ApiService';
import type { DeviceSummary, HistoryPoint } from '../types';
import type { Device } from '../types';
import TemperatureChart from '../components/TemperatureChart';
import WebSocketService from '../services/WebSocketService';

import WhatshotIcon from '@mui/icons-material/Whatshot';
import VisibilityIcon from '@mui/icons-material/Visibility';

import { getStoredDeviceId, clearStoredDeviceId } from '../utils/StorageLastDevice';

// ━━━ Constants ━━━
const HISTORY_LIMIT = 200;
const DATA_STALE_TIMEOUT = 10000;

const DashboardPage: React.FC = () => {
    const theme = useTheme();
    const [popularDevices, setPopularDevices] = useState<DeviceSummary[]>([])
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

    // ━━━ Initial Load ━━━
    useEffect(() => {
        const loadDashboard = async () => {
            setLoading(true);
            try {
                try {
                    const dashboardStats = await getDashboardStats();
                    setPopularDevices(dashboardStats.popularDevices ?? []);
                } catch (error) {
                    console.error("Failed to load dashboard stats:", error);
                    setPopularDevices([]);  // Явно устанавливаем пустой список
                }

                const localId = getStoredDeviceId();

                if (localId) {
                    try {
                        const deviceData = await getDeviceById(localId);
                        setLastDevice(deviceData);

                        const history = await getDeviceHistory(localId);
                        setChartData(history);
                    } catch (error) {
                        console.warn(`Device ${localId} not found or deleted:`, error);
                        clearStoredDeviceId();
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
                            minHeight: 260
                        }}
                    >
                        <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <WhatshotIcon color="error" />
                            Beliebteste Geräte
                        </Typography>
                        <Grid container spacing={2}>
                            {popularDevices.length > 0 ? (
                                popularDevices.map((dev, index) => (
                                    <Grid size={{ xs: 12, md: 4, sm: 6 }} key={dev.deviceId}>
                                        <Card elevation={0} sx={{ border: `1px solid ${theme.palette.divider}`, borderRadius: 2 }}>
                                            <CardActionArea component={RouterLink} to={`/devices/${dev.deviceId}`} sx={{ p: 2 }}>
                                                <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                                                    <Box sx={{ overflow: 'hidden' }}>
                                                        <Typography variant="subtitle1" fontWeight="bold" noWrap title={dev.name || dev.deviceId}>
                                                            {dev.name || dev.deviceId}
                                                        </Typography>
                                                        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                                                            ID: {dev.deviceId.substring(0, 8)}...
                                                        </Typography>
                                                    </Box>
                                                    {/* Номер в топе */}
                                                    <Avatar sx={{
                                                        width: 24, height: 24, fontSize: 12,
                                                        bgcolor: index === 0 ? 'gold' : theme.palette.action.selected,
                                                        color: index === 0 ? 'black' : 'text.primary'
                                                    }}>
                                                        {index + 1}
                                                    </Avatar>
                                                </Stack>
                                            </CardActionArea>
                                        </Card>
                                    </Grid>
                                ))
                            ) : (
                                <Grid size={{ xs: 12 }}>
                                    <Paper elevation={0} sx={{ p: 3, bgcolor: '#f9f9f9', textAlign: 'center', color: 'text.secondary', border: `1px dashed ${theme.palette.divider}` }}>
                                        Noch keine Statistik verfügbar. Nutzen Sie die App, um Daten zu sammeln.
                                    </Paper>
                                </Grid>
                            )}
                        </Grid>
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