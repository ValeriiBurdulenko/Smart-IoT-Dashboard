import React, { useState, useEffect } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import {
    Box, Typography, Breadcrumbs, Link, Paper, Grid,
    TextField, IconButton, Button, Stack, Divider, InputAdornment, Slider, Tooltip,
    CircularProgress, Snackbar, Alert, Chip, useTheme
} from '@mui/material';
import {
    AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer
} from 'recharts';
import { format } from 'date-fns';

import EditIcon from '@mui/icons-material/Edit';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import SendIcon from '@mui/icons-material/Send';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import KeyboardIcon from '@mui/icons-material/Keyboard';
import WhatshotIcon from '@mui/icons-material/Whatshot';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import AcUnitIcon from '@mui/icons-material/AcUnit';
import SignalWifiOffIcon from '@mui/icons-material/SignalWifiOff';

import type { Device } from '../types';
import { getDeviceById, updateDeviceName, sendTemperatureCommand, getDeviceHistory } from '../services/ApiService';
import type { HistoryPoint } from '../services/ApiService';
import WebSocketService from '../services/WebSocketService';
import type { TelemetryData, ConnectionStatus } from '../services/WebSocketService';

type SnackbarState = {
    open: boolean;
    message: string;
    severity: 'success' | 'error' | 'warning';
} | null;

const DeviceDetailPage: React.FC = () => {
    const { id: deviceId } = useParams<{ id: string }>();
    const theme = useTheme();

    // --- STATE ---
    const [device, setDevice] = useState<Device | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [snackbar, setSnackbar] = useState<SnackbarState>(null);

    // WebSocket State
    const [wsStatus, setWsStatus] = useState<ConnectionStatus>(WebSocketService.getStatus());
    const [liveData, setLiveData] = useState<TelemetryData['data'] | null>(null);
    const [historyData, setHistoryData] = useState<HistoryPoint[]>([]);

    // Name editing
    const [isEditingName, setIsEditingName] = useState(false);
    const [editedName, setEditedName] = useState("");
    const [isSavingName, setIsSavingName] = useState(false);

    // Temperature control
    const [targetTemp, setTargetTemp] = useState<number>(22);
    const [isManualInput, setIsManualInput] = useState(false);
    const [manualTempValue, setManualTempValue] = useState<string>("22");
    const [manualInputError, setManualInputError] = useState<string | null>(null);
    const [sliderBounds, setSliderBounds] = useState({ min: 10, max: 30 });
    const [isSendingCommand, setIsSendingCommand] = useState(false);
    const [isDataStale, setIsDataStale] = useState(false);

    const GLOBAL_MIN = -40;
    const GLOBAL_MAX = 100;

    // Initial Data Load (REST)
    useEffect(() => {
        fetchDevice();
        if (deviceId) {
            getDeviceHistory(deviceId)
                .then(data => setHistoryData(data))
                .catch(err => console.error("History load failed", err));
        }
    }, [deviceId]);

    // WebSocket Status Monitoring
    useEffect(() => {
        const unsubscribe = WebSocketService.onStatusChange((status) => {
            setWsStatus(status);
        });
        return () => unsubscribe();
    }, []);

    // WebSocket Data Subscription
    useEffect(() => {
        if (!device || !device.deviceId) return;

        console.log(`📡 Subscribing to live data for ${device.deviceId}`);

        const subscription = WebSocketService.subscribeToDevice(device.deviceId, (telemetry) => {
            setLiveData(telemetry.data);

            setHistoryData(prev => {
                const newPoint = {
                    timestamp: telemetry.timestamp,
                    temperature: telemetry.data.currentTemperature
                };
                const newData = [...prev, newPoint];
                if (newData.length > 300) return newData.slice(newData.length - 300);
                return newData;
            });
        });

        return () => {
            console.log(`🔕 Unsubscribing from ${device.deviceId}`);
            subscription.unsubscribe();
        };
    }, [device?.deviceId]);

    const fetchDevice = () => {
        setLoading(true);
        getDeviceById(deviceId!)
            .then(data => {
                setDevice(data);
                setEditedName(data.name || data.deviceId);
                if (data.targetTemperature !== undefined && data.targetTemperature !== null) {
                    const val = data.targetTemperature;
                    setTargetTemp(val);
                    if (val < 10 || val > 30) {
                        setSliderBounds({
                            min: Math.max(GLOBAL_MIN, Math.floor(val - 10)),
                            max: Math.min(GLOBAL_MAX, Math.ceil(val + 10))
                        });
                    }
                }
                setError(null);
            })
            .catch(err => {
                console.error(err);
                setError("Gerät nicht gefunden (404)");
            })
            .finally(() => setLoading(false));
    }

    // --- NAME HANDLERS ---
    const handleStartEditName = () => {
        setIsEditingName(true);
        setEditedName(device?.name || device?.deviceId || "");
    };

    const handleCancelEditName = () => {
        setEditedName(device?.name || device?.deviceId || "");
        setIsEditingName(false);
    };

    const handleSaveName = () => {
        if (!device || !editedName.trim()) return;

        setIsSavingName(true);
        updateDeviceName(device.deviceId, editedName)
            .then((updatedDevice) => {
                setDevice(updatedDevice);
                setIsEditingName(false);
                setSnackbar({
                    open: true,
                    message: "Gerätename erfolgreich aktualisiert",
                    severity: 'success'
                });
            })
            .catch(err => {
                console.error("Failed to rename", err);
                setSnackbar({
                    open: true,
                    message: "Fehler beim Umbenennen des Geräts",
                    severity: 'error'
                });
            })
            .finally(() => setIsSavingName(false));
    };

    // --- TEMPERATURE HANDLERS ---
    const handleSliderChange = (event: Event, newValue: number | number[]) => {
        setTargetTemp(newValue as number);
    };

    const handleEnterManualMode = () => {
        setManualTempValue(targetTemp.toString());
        setManualInputError(null);
        setIsManualInput(true);
    };

    const handleCancelManualMode = () => {
        setIsManualInput(false);
        setManualInputError(null);
    };

    const handleSaveManualMode = () => {
        const val = parseFloat(manualTempValue);

        // Валидация
        if (isNaN(val)) {
            setManualInputError("Ungültige Zahl");
            return;
        }

        if (val < GLOBAL_MIN || val > GLOBAL_MAX) {
            setManualInputError(`Bereich: ${GLOBAL_MIN}°C bis ${GLOBAL_MAX}°C`);
            return;
        }

        setTargetTemp(val);

        const newMin = Math.max(GLOBAL_MIN, Math.floor(val - 10));
        const newMax = Math.min(GLOBAL_MAX, Math.ceil(val + 10));
        setSliderBounds({ min: newMin, max: newMax });

        setIsManualInput(false);
        setManualInputError(null);
    };

    const handleSendTemperature = () => {
        if (!device) return;
        setIsSendingCommand(true);

        sendTemperatureCommand(device.deviceId, targetTemp)
            .then(() => {
                setSnackbar({
                    open: true,
                    message: "Befehl gesendet! Das Gerät wird aktualisiert, sobald es online ist.",
                    severity: 'success'
                });
            })
            .catch(err => {
                console.error("Command failed", err);
                setSnackbar({
                    open: true,
                    message: "Fehler: Keine Verbindung zum Broker. Versuchen Sie es später erneut.",
                    severity: 'error'
                });
            })
            .finally(() => setIsSendingCommand(false));
    };

    const handleCloseSnackbar = () => {
        setSnackbar(null);
    };

    useEffect(() => {
        if (wsStatus !== 'connected') return;

        setIsDataStale(false);

        const timer = setTimeout(() => {
            console.warn("Data not update 10 seconds. Think that device is OFFLINE.");
            setIsDataStale(true);
        }, 10000);

        return () => clearTimeout(timer);

    }, [liveData]);

    if (loading) return <Box p={3}><CircularProgress /></Box>;
    if (error || !device) return <Box p={3}><Typography color="error">{error || "Gerät nicht gefunden"}</Typography></Box>;

    const effectiveTarget = liveData?.targetTemperature ?? targetTemp;
    const isHeating = liveData?.heatingStatus && (liveData.currentTemperature < (effectiveTarget - 0.1));
    const isCooling = liveData?.heatingStatus && (liveData.currentTemperature > (effectiveTarget + 0.1));

    const isSocketActive = wsStatus === 'connected';
    const showOfflineAlert = !isSocketActive || isDataStale;

    const offlineMessage = !isSocketActive
        ? "Verbindung unterbrochen"
        : "Keine Daten vom Gerät";

    return (
        <Box sx={{ width: '100%' }}>

            {/* Breadcrumbs */}
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                <Breadcrumbs
                    separator={<NavigateNextIcon fontSize="small" />}
                    aria-label="breadcrumb"
                    sx={{ mb: 3 }}
                >
                    <Link component={RouterLink} underline="hover" color="inherit" to="/devices">
                        Geräte
                    </Link>
                    <Typography color="text.primary">
                        {device.name || device.deviceId}
                    </Typography>
                </Breadcrumbs>
            </Box>

            <Grid container spacing={3}>

                {/* Device Info & Name */}
                <Grid size={{ xs: 12, md: 6 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            p: 3,
                            border: '1px solid ${theme.palette.divider}',
                            borderRadius: 2,
                            height: '100%'
                        }}
                    >
                        <Typography variant="overline" color="text.secondary">
                            Gerätename
                        </Typography>

                        {isEditingName ? (
                            <Stack direction="row" spacing={1} alignItems="center" mt={1}>
                                <TextField
                                    fullWidth
                                    size="small"
                                    value={editedName}
                                    onChange={(e) => setEditedName(e.target.value)}
                                    disabled={isSavingName}
                                    autoFocus
                                />
                                <IconButton
                                    color="success"
                                    onClick={handleSaveName}
                                    disabled={isSavingName}
                                    sx={{ border: '1px solid', borderColor: 'success.light' }}
                                >
                                    {isSavingName ? <CircularProgress size={20} /> : <CheckIcon />}
                                </IconButton>
                                <IconButton
                                    color="error"
                                    onClick={handleCancelEditName}
                                    disabled={isSavingName}
                                    sx={{ border: '1px solid', borderColor: 'error.light' }}
                                >
                                    <CloseIcon />
                                </IconButton>
                            </Stack>
                        ) : (
                            <Stack direction="row" spacing={1} alignItems="center" mt={1}>
                                <Typography variant="h5" fontWeight="bold">
                                    {device.name || device.deviceId}
                                </Typography>
                                <IconButton onClick={handleStartEditName} size="small">
                                    <EditIcon fontSize="small" />
                                </IconButton>
                            </Stack>
                        )}

                        <Divider sx={{ my: 2 }} />

                        <Typography variant="overline" color="text.secondary">
                            Technische ID
                        </Typography>
                        <Typography variant="body1" sx={{ fontFamily: 'monospace', bgcolor: '#f5f5f5', p: 1, borderRadius: 1 }}>
                            {device.deviceId}
                        </Typography>
                    </Paper>
                </Grid>

                {/* Temperature Control */}
                <Grid size={{ xs: 12, md: 6 }}>
                    <Paper elevation={0} sx={{ p: 3, border: '1px solid ${theme.palette.divider}', borderRadius: 2, height: '100%' }}>

                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                            <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center' }}>
                                <ThermostatIcon sx={{ mr: 1, color: 'primary.main' }} />
                                Steuerung
                            </Typography>

                            {!isManualInput && (
                                <Tooltip title="Manuell eingeben">
                                    <IconButton onClick={handleEnterManualMode} size="small">
                                        <KeyboardIcon />
                                    </IconButton>
                                </Tooltip>
                            )}
                        </Box>

                        <Box sx={{ mt: 2, px: 1 }}>

                            {isManualInput ? (
                                <Stack spacing={2}>
                                    <Typography variant="body2" color="text.secondary">
                                        Geben Sie einen Wert zwischen {GLOBAL_MIN} und {GLOBAL_MAX} ein:
                                    </Typography>
                                    <Stack direction="row" spacing={1}>
                                        <TextField
                                            type="number"
                                            fullWidth
                                            value={manualTempValue}
                                            onChange={(e) => {
                                                setManualTempValue(e.target.value);
                                                setManualInputError(null);
                                            }}
                                            error={!!manualInputError}
                                            helperText={manualInputError}
                                            InputProps={{
                                                endAdornment: <InputAdornment position="end">°C</InputAdornment>,
                                            }}
                                            autoFocus
                                        />
                                        <Button
                                            variant="contained"
                                            onClick={handleSaveManualMode}
                                            disabled={!!manualInputError}
                                        >
                                            OK
                                        </Button>
                                        <Button
                                            variant="outlined"
                                            color="error"
                                            onClick={handleCancelManualMode}
                                        >
                                            Abbrechen
                                        </Button>
                                    </Stack>
                                </Stack>
                            ) : (
                                <>
                                    <Typography
                                        fontWeight="bold"
                                        color="primary.main"
                                        align="center"
                                        gutterBottom
                                        sx={{ fontSize: 32 }}
                                    >
                                        {targetTemp}°C
                                    </Typography>

                                    <Stack spacing={2} direction="row" alignItems="center" sx={{ mt: 3 }}>
                                        <Typography variant="caption" color="text.secondary">
                                            {sliderBounds.min}°
                                        </Typography>
                                        <Slider
                                            value={targetTemp}
                                            onChange={handleSliderChange}
                                            aria-label="Temperature"
                                            step={0.1}
                                            min={sliderBounds.min}
                                            max={sliderBounds.max}
                                            valueLabelDisplay="auto"
                                            sx={{ flexGrow: 1 }}
                                        />
                                        <Typography variant="caption" color="text.secondary">
                                            {sliderBounds.max}°
                                        </Typography>
                                    </Stack>

                                    <Button
                                        variant="contained"
                                        startIcon={isSendingCommand ? <CircularProgress size={20} color="inherit" /> : <SendIcon />}
                                        onClick={handleSendTemperature}
                                        disabled={isSendingCommand}
                                        fullWidth
                                        sx={{ mt: 3, boxShadow: 'none', fontWeight: 600 }}
                                    >
                                        {isSendingCommand ? "Senden..." : "Temperatur Senden"}
                                    </Button>
                                </>
                            )}
                        </Box>
                    </Paper>
                </Grid>

                {/* Analytics Placeholder */}
                <Grid size={{ xs: 12 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            p: 3,
                            border: '1px solid ${theme.palette.divider}',
                            borderRadius: 2,
                            minHeight: 400,
                            overflow: 'hidden'
                        }}
                    >
                        <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <Typography variant="h6" fontWeight="bold">Echtzeit-Analyse</Typography>
                            {isSocketActive && liveData && !isDataStale ? (
                                <Chip
                                    icon={<FiberManualRecordIcon sx={{ fontSize: '10px !important', color: '#4caf50', animation: 'pulse 1.5s infinite' }} />}
                                    label="LIVE"
                                    size="small"
                                    variant="outlined"
                                    sx={{ fontWeight: 'bold', borderColor: '#4caf50', color: '#2e7d32', bgcolor: 'rgba(76, 175, 80, 0.1)', '@keyframes pulse': { '0%': { opacity: 1 }, '50%': { opacity: 0.5 }, '100%': { opacity: 1 } } }}
                                />
                            ) : showOfflineAlert ? (
                                <Chip
                                    icon={<SignalWifiOffIcon />}
                                    label="OFFLINE"
                                    size="small"
                                    color="error"
                                    variant="outlined"
                                    sx={{ fontWeight: 'bold' }}
                                />
                            ) : null}
                        </Box>
                        <Divider sx={{ my: 2 }} />
                        <Box sx={{ p: 3 }}>
                            <Grid container spacing={4} alignItems="center">
                                {/* (KPI) */}
                                <Grid size={{ xs: 12, md: 3 }}>
                                    <Stack spacing={4}>
                                        {/* Current Temp */}
                                        <Box>
                                            <Typography variant="caption" color="text.secondary" fontWeight="bold" letterSpacing={1}>AKTUELLE TEMP</Typography>
                                            <Typography variant="h2" color="primary.main" fontWeight="bold" sx={{ mt: 1 }}>
                                                {liveData ? (
                                                    <>
                                                        {liveData.currentTemperature.toFixed(1)}
                                                        <Typography component="span" variant="h5" color="text.secondary" sx={{ ml: 1 }}>°C</Typography>
                                                    </>
                                                ) : (
                                                    showOfflineAlert ? "--.-" : <CircularProgress size={40} thickness={5} sx={{ mt: 1 }} />
                                                )}
                                            </Typography>
                                        </Box>

                                        {/* Status */}
                                        <Box>
                                            <Typography variant="caption" color="text.secondary" fontWeight="bold" letterSpacing={1}>STATUS</Typography>
                                            <Box sx={{ mt: 1 }}>
                                                {liveData ? (
                                                    <Chip
                                                        icon={isHeating ? <WhatshotIcon /> : (isCooling ? <AcUnitIcon /> : undefined)}
                                                        label={isHeating ? "HEIZT AUF" : (isCooling ? "KÜHLT AB" : "IDLE")}
                                                        color={isHeating ? "error" : (isCooling ? "info" : "default")}
                                                        variant={isHeating || isCooling ? "filled" : "outlined"}
                                                        sx={{ fontWeight: 'bold', px: 1, height: 32 }}
                                                    />
                                                ) : (
                                                    showOfflineAlert ? "" : <CircularProgress size={20} color="inherit" />
                                                )}
                                            </Box>
                                        </Box>
                                    </Stack>
                                </Grid>

                                <Grid size={{ xs: 12, md: 9 }}>
                                    <Box sx={{ width: '100%', height: 350, minHeight: 350, bgcolor: '#fbfbfb', borderRadius: 2, border: '1px dashed ${theme.palette.divider}', position: 'relative', overflow: 'hidden' }}>
                                        {showOfflineAlert && (
                                            <Box
                                                sx={{
                                                    position: 'absolute',
                                                    top: 0, left: 0, right: 0, bottom: 0,
                                                    bgcolor: 'rgba(255, 255, 255, 0.7)',
                                                    backdropFilter: 'blur(2px)',
                                                    zIndex: 10,
                                                    display: 'flex',
                                                    flexDirection: 'column',
                                                    alignItems: 'center',
                                                    justifyContent: 'center',
                                                    color: 'text.secondary'
                                                }}
                                            >
                                                <SignalWifiOffIcon sx={{ fontSize: 48, mb: 1 }} />
                                                <Typography variant="h6" fontWeight="bold">{offlineMessage}</Typography>
                                            </Box>
                                        )}
                                        {historyData.length > 0 ? (
                                            <ResponsiveContainer width="100%" height="100%">
                                                <AreaChart data={historyData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                                                    <defs>
                                                        <linearGradient id="colorTemp" x1="0" y1="0" x2="0" y2="1">
                                                            <stop offset="5%" stopColor="#1976d2" stopOpacity={0.3} />
                                                            <stop offset="95%" stopColor="#1976d2" stopOpacity={0} />
                                                        </linearGradient>
                                                    </defs>
                                                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#eee" />
                                                    <XAxis
                                                        dataKey="timestamp"
                                                        tickFormatter={(str) => format(new Date(str), 'HH:mm')}
                                                        stroke="#aaa" fontSize={12} minTickGap={50} tickLine={false} axisLine={false}
                                                    />
                                                    <YAxis
                                                        domain={['auto', 'auto']}
                                                        stroke="#aaa" fontSize={12} unit="°" tickLine={false} axisLine={false} width={40}
                                                    />
                                                    <RechartsTooltip
                                                        labelFormatter={(label) => format(new Date(label), 'dd.MM.yyyy HH:mm:ss')}
                                                        formatter={(value: number) => [`${value.toFixed(1)}°C`, 'Temperatur']}
                                                        contentStyle={{ borderRadius: 8, border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}
                                                    />
                                                    <Area
                                                        type="monotone"
                                                        dataKey="temperature"
                                                        stroke="#1976d2"
                                                        strokeWidth={3}
                                                        fill="url(#colorTemp)"
                                                        animationDuration={500}
                                                        isAnimationActive={false}
                                                        connectNulls
                                                    />
                                                </AreaChart>
                                            </ResponsiveContainer>
                                        ) : (
                                            <Box sx={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column' }}>
                                                {liveData && !showOfflineAlert ? (
                                                    <Typography color="text.secondary">Sammle erste Datenpunkte...</Typography>
                                                ) : !showOfflineAlert ? (
                                                    <>
                                                        <CircularProgress size={30} sx={{ mb: 2, color: 'text.disabled' }} />
                                                        <Typography color="text.disabled">Warte auf Verbindung...</Typography>
                                                    </>
                                                ) : null}
                                            </Box>
                                        )}
                                    </Box>
                                </Grid>
                            </Grid>
                        </Box>
                    </Paper>
                </Grid>

            </Grid>

            {/* Unified Snackbar */}
            <Snackbar
                open={snackbar?.open ?? false}
                autoHideDuration={4000}
                onClose={handleCloseSnackbar}
            >
                <Alert
                    onClose={handleCloseSnackbar}
                    severity={snackbar?.severity ?? 'success'}
                    sx={{ width: '100%' }}
                >
                    {snackbar?.message}
                </Alert>
            </Snackbar>
        </Box >
    );
};

export default DeviceDetailPage;