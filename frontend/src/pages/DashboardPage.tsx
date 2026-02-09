import React, { useState, useEffect, useRef } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
    Grid, Paper, Typography, Box, Button, CircularProgress,
    Card, CardActionArea, Stack, useTheme, Avatar,
    List, ListItem, ListItemText, ListItemAvatar, Divider
} from '@mui/material';
import DevicesIcon from '@mui/icons-material/Devices';

import { getDeviceHistory, getDeviceById, getDashboardStats } from '../services/ApiService';
import type { DashboardDevice, HistoryPoint, DashboardAlert } from '../types';
import type { Device } from '../types';
import TemperatureChart from '../components/TemperatureChart';
import WebSocketService from '../services/WebSocketService';

import WhatshotIcon from '@mui/icons-material/Whatshot';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import SignalWifiOffIcon from '@mui/icons-material/SignalWifiOff';
import CloudOffIcon from '@mui/icons-material/CloudOff';

import { getStoredDeviceId, clearStoredDeviceId } from '../utils/StorageLastDevice';

// Constants
const HISTORY_LIMIT = 200;
const DATA_STALE_TIMEOUT = 10000;

const DashboardPage: React.FC = () => {
    const theme = useTheme();

    // State
    const [popularDevices, setPopularDevices] = useState<DashboardDevice[]>([]);
    const [lastDevice, setLastDevice] = useState<Device | null>(null);
    const [chartData, setChartData] = useState<HistoryPoint[]>([]);
    const [loading, setLoading] = useState(true);
    const [recentAlerts, setRecentAlerts] = useState<DashboardAlert[]>([]);

    // UI State for connectivity
    const [isDataStale, setIsDataStale] = useState(false);
    const [wsStatus, setWsStatus] = useState(WebSocketService.getStatus());

    // Refs
    const subscriptionRef = useRef<any>(null);
    const alertsSubscriptionRef = useRef<any>(null);
    const staleTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Error State
    const [statsError, setStatsError] = useState(false);

    // Button Style
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
        let isMounted = true;

        const loadDashboard = async () => {
            if (isMounted) setLoading(true);

            try {
                try {
                    setStatsError(false);
                    const dashboardStats = await getDashboardStats();
                    if (isMounted) {
                        setPopularDevices(dashboardStats.popularDevices ?? []);
                        setRecentAlerts(dashboardStats.recentAlerts ?? []);
                    }
                } catch (error) {
                    console.error("Failed to load dashboard stats:", error);
                    if (isMounted) {
                        setStatsError(true);
                        setPopularDevices([]);
                        setRecentAlerts([]);
                    }
                }

                const localId = getStoredDeviceId();

                if (localId) {
                    try {
                        const deviceData = await getDeviceById(localId);
                        const history = await getDeviceHistory(localId);

                        if (isMounted) {
                            setLastDevice(deviceData);
                            setChartData(history);
                        }
                    } catch (error) {
                        console.warn(`Device ${localId} not found or deleted:`, error);
                        clearStoredDeviceId();
                    }
                }
            } catch (error) {
                console.error('Dashboard load failed:', error);
            } finally {
                if (isMounted) setLoading(false);
            }
        };

        loadDashboard();
    }, [getStoredDeviceId]);

    // WebSocket Status
    useEffect(() => {
        const unsubscribe = WebSocketService.onStatusChange((status) => {
            setWsStatus(status);
            if (status !== 'connected') {
                setIsDataStale(true);
            }
        });
        return () => unsubscribe();
    }, []);

    // WebSocket Subscription
    useEffect(() => {
        if (!lastDevice?.deviceId) return;

        // Clear previous timeout
        const resetStaleTimer = () => {
            if (staleTimeoutRef.current) clearTimeout(staleTimeoutRef.current);
            staleTimeoutRef.current = setTimeout(() => {
                setIsDataStale(true);
            }, DATA_STALE_TIMEOUT);
        };

        resetStaleTimer();

        const telemetrySub = WebSocketService.subscribeToDevice(
            lastDevice.deviceId,
            (telemetry) => {
                const newPoint: HistoryPoint = {
                    timestamp: telemetry.timestamp,
                    temperature: telemetry.data.currentTemperature
                };

                setIsDataStale(false);
                resetStaleTimer();

                setChartData((prev) => {
                    const newData = [...prev, newPoint];
                    return newData.length > HISTORY_LIMIT
                        ? newData.slice(newData.length - HISTORY_LIMIT)
                        : newData;
                });
            }
        );

        subscriptionRef.current = telemetrySub;

        const alertsSub = WebSocketService.subscribeToUserAlerts(lastDevice.deviceId,
            (alertData) => {
                setStatsError(false);
                const newAlert: DashboardAlert = {
                    alertId: alertData.alertId,
                    deviceId: alertData.deviceId,
                    type: alertData.type as any,
                    timestamp: alertData.timestamp
                };

                setRecentAlerts(prev => [newAlert, ...prev].slice(0, 5));
            }
        );

        alertsSubscriptionRef.current = alertsSub;

        return () => {
            subscriptionRef.current?.unsubscribe();
            alertsSubscriptionRef.current?.unsubscribe();
            if (staleTimeoutRef.current) {
                clearTimeout(staleTimeoutRef.current);
            }
        };
    }, [lastDevice?.deviceId]);

    const getAlertIcon = (type: string) => {
        if (type.includes('CRITICAL') || type === 'MALFUNCTION' || type === 'SECURITY_SPAM') return <ErrorIcon color="error" />;
        if (type.includes('WARNING') || type === 'STUCK' || type === 'OFFLINE') return <WarningIcon color="warning" />;
        return <InfoIcon color="info" />;
    };

    const formatTime = (timestamp: number) => {
        if (!timestamp) return '';
        return new Date(timestamp).toLocaleTimeString('de-DE', {
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    const isSocketActive = wsStatus === 'connected';
    const showOfflineWarning = !isSocketActive || isDataStale;

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
                                    isOffline={showOfflineWarning}
                                    offlineMessage={!isSocketActive ? 'Keine Verbindung zum Server' : 'Keine Daten vom Gerät'}
                                />
                                {showOfflineWarning && (
                                    <Box sx={{
                                        position: 'absolute', top: 10, right: 10,
                                        display: 'flex', alignItems: 'center', gap: 1,
                                        bgcolor: 'error.light', color: 'error.contrastText',
                                        px: 1, py: 0.5, borderRadius: 1, opacity: 0.9
                                    }}>
                                        <SignalWifiOffIcon fontSize="small" />
                                        <Typography variant="caption" fontWeight="bold">OFFLINE</Typography>
                                    </Box>
                                )}
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
                                        {statsError ? 'Fehler beim Laden der Daten' : 'Noch keine Statistik verfügbar. Nutzen Sie die App, um Daten zu sammeln.'}
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
                            height: '100%'
                        }}
                    >
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                            <Typography variant="h6" fontWeight="bold" sx={{ display: 'flex', alignItems: 'center', gap: 1, fontSize: '1rem' }}>
                                <NotificationsActiveIcon color="warning" fontSize="small" /> Letzte Warnungen
                            </Typography>
                            <Button component={RouterLink} to="/alerts" size="small" sx={{ fontSize: '0.7rem', minWidth: 'auto' }}>
                                Alle
                            </Button>
                        </Box>

                        <List sx={{ p: 0 }}>
                            {recentAlerts.length > 0 ? (
                                recentAlerts.slice(0, 4).map((alert, index) => (
                                    <React.Fragment key={alert.alertId}>
                                        <ListItem alignItems="flex-start" disableGutters>
                                            <ListItemAvatar sx={{ minWidth: 40 }}>
                                                {getAlertIcon(alert.type)}
                                            </ListItemAvatar>
                                            <ListItemText
                                                primary={
                                                    <Typography variant="subtitle2" sx={{ fontSize: '0.85rem', fontWeight: 'bold' }}>
                                                        {alert.type}
                                                    </Typography>
                                                }
                                                secondary={
                                                    <Typography variant="caption" display="block" color="text.secondary">
                                                        {formatTime(alert.timestamp)} • {alert.deviceId.substring(0, 4)}...
                                                    </Typography>
                                                }
                                            />
                                        </ListItem>
                                        {index < recentAlerts.slice(0, 4).length - 1 && <Divider component="li" variant="inset" />}
                                    </React.Fragment>
                                ))
                            ) : statsError ? (
                                <Box sx={{ p: 4, textAlign: 'center', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                                    <CloudOffIcon color="error" sx={{ fontSize: 40, mb: 1, opacity: 0.5, mx: 'auto' }} />
                                    <Typography variant="body2" color="error">
                                        Daten konnten nicht geladen werden
                                    </Typography>
                                </Box>
                            ) : (
                                <Box sx={{ p: 4, textAlign: 'center', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                                    <CheckCircleIcon color="success" sx={{ fontSize: 40, mb: 1, opacity: 0.5, mx: 'auto' }} />
                                    <Typography variant="body2" color="text.secondary">Alles in Ordnung</Typography>
                                </Box>
                            )}
                        </List>
                    </Paper>
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            border: `1px solid ${theme.palette.divider}`,
                            p: 2,
                            height: '100%'
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