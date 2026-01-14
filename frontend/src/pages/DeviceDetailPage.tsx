import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import {
    Box, Typography, Breadcrumbs, Link, Paper, Grid,
    TextField, IconButton, Button, Stack, Divider, InputAdornment, Slider, Tooltip,
    CircularProgress, Snackbar, Alert, Chip, useTheme
} from '@mui/material';

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
import {
    getDeviceById,
    updateDeviceName,
    sendTemperatureCommand,
    getDeviceHistory
} from '../services/ApiService';
import type { HistoryPoint } from '../services/ApiService';
import WebSocketService from '../services/WebSocketService';
import type { TelemetryData, ConnectionStatus } from '../services/WebSocketService';
import TemperatureChart from '../components/TemperatureChart';

// ━━━ Constants ━━━
const GLOBAL_MIN = -40;
const GLOBAL_MAX = 100;
const HISTORY_LIMIT = 300;
const DATA_STALE_TIMEOUT = 10000; // 10 seconds
const SLIDER_PADDING = 10;
const STORAGE_KEY = 'last_device_id';

type SnackbarState = {
    open: boolean;
    message: string;
    severity: 'success' | 'error' | 'warning';
} | null;

const DeviceDetailPage: React.FC = () => {
    const { id: deviceId } = useParams<{ id: string }>();
    const theme = useTheme();

    // ━━━ State ━━━
    const [device, setDevice] = useState<Device | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [snackbar, setSnackbar] = useState<SnackbarState>(null);

    // WebSocket
    const [wsStatus, setWsStatus] = useState<ConnectionStatus>(
        WebSocketService.getStatus()
    );
    const [liveData, setLiveData] = useState<TelemetryData['data'] | null>(null);
    const [historyData, setHistoryData] = useState<HistoryPoint[]>([]);
    const [isDataStale, setIsDataStale] = useState(false);

    // Name Editing
    const [isEditingName, setIsEditingName] = useState(false);
    const [editedName, setEditedName] = useState('');
    const [isSavingName, setIsSavingName] = useState(false);

    // Temperature Control
    const [targetTemp, setTargetTemp] = useState<number>(22);
    const [isManualInput, setIsManualInput] = useState(false);
    const [manualTempValue, setManualTempValue] = useState<string>('22');
    const [manualInputError, setManualInputError] = useState<string | null>(null);
    const [sliderBounds, setSliderBounds] = useState({ min: 10, max: 30 });
    const [isSendingCommand, setIsSendingCommand] = useState(false);

    // Refs
    const abortControllerRef = useRef<AbortController | null>(null);
    const subscriptionRef = useRef<any>(null);
    const staleTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // ━━━ Temperature Validation ━━━
    const validateTemperature = useCallback((value: number): boolean => {
        return value >= GLOBAL_MIN && value <= GLOBAL_MAX;
    }, []);

    const updateSliderBounds = useCallback((temp: number): void => {
        const newMin = Math.max(GLOBAL_MIN, Math.floor(temp - SLIDER_PADDING));
        const newMax = Math.min(GLOBAL_MAX, Math.ceil(temp + SLIDER_PADDING));
        setSliderBounds({ min: newMin, max: newMax });
    }, []);

    // ━━━ Safe localStorage ━━━
    const setStoredDeviceId = useCallback((id: string): void => {
        try {
            if (typeof window !== 'undefined') {
                localStorage.setItem(STORAGE_KEY, id);
            }
        } catch (e) {
            console.warn('localStorage unavailable:', e);
        }
    }, []);

    // ━━━ Initial Device Load ━━━
    const fetchDevice = useCallback(async () => {
        if (!deviceId) return;

        abortControllerRef.current = new AbortController();
        setLoading(true);
        setError(null);

        try {
            const data = await getDeviceById(deviceId);
            setDevice(data);
            setEditedName(data.name || data.deviceId);

            if (
                data.targetTemperature !== undefined &&
                data.targetTemperature !== null
            ) {
                const temp = data.targetTemperature;
                if (validateTemperature(temp)) {
                    setTargetTemp(temp);
                    updateSliderBounds(temp);
                }
            }

            setStoredDeviceId(deviceId);

            const history = await getDeviceHistory(deviceId);
            setHistoryData(history);
        } catch (err) {
            if (err instanceof Error && err.name === 'AbortError') {
                return;
            }
            console.error('Device load error:', err);
            setError('Gerät nicht gefunden (404)');
        } finally {
            setLoading(false);
        }
    }, [deviceId, validateTemperature, updateSliderBounds, setStoredDeviceId]);

    useEffect(() => {
        fetchDevice();
        return () => {
            abortControllerRef.current?.abort();
        };
    }, [fetchDevice]);

    // ━━━ WebSocket Status Monitoring ━━━
    useEffect(() => {
        const unsubscribe = WebSocketService.onStatusChange((status) => {
            setWsStatus(status);
        });
        return () => unsubscribe();
    }, []);

    // ━━━ WebSocket Data Subscription ━━━
    useEffect(() => {
        if (!device?.deviceId) return;

        console.log(`📡 Subscribing to live data for ${device.deviceId}`);

        const subscription = WebSocketService.subscribeToDevice(
            device.deviceId,
            (telemetry) => {
                setLiveData(telemetry.data);
                setIsDataStale(false);

                // Reset stale timer
                if (staleTimeoutRef.current) {
                    clearTimeout(staleTimeoutRef.current);
                }

                setHistoryData((prev) => {
                    const newPoint: HistoryPoint = {
                        timestamp: telemetry.timestamp,
                        temperature: telemetry.data.currentTemperature
                    };
                    const newData = [...prev, newPoint];
                    return newData.length > HISTORY_LIMIT
                        ? newData.slice(newData.length - HISTORY_LIMIT)
                        : newData;
                });
            }
        );

        subscriptionRef.current = subscription;

        return () => {
            console.log(`🔕 Unsubscribing from ${device.deviceId}`);
            subscriptionRef.current?.unsubscribe();
            if (staleTimeoutRef.current) {
                clearTimeout(staleTimeoutRef.current);
            }
        };
    }, [device?.deviceId]);

    // ━━━ Data Stale Detection ━━━
    useEffect(() => {
        if (wsStatus !== 'connected') {
            setIsDataStale(true);
            return;
        }

        setIsDataStale(false);

        staleTimeoutRef.current = setTimeout(() => {
            console.warn('Data not updated for 10 seconds');
            setIsDataStale(true);
        }, DATA_STALE_TIMEOUT);

        return () => {
            if (staleTimeoutRef.current) {
                clearTimeout(staleTimeoutRef.current);
            }
        };
    }, [liveData, wsStatus]);

    // ━━━ Name Handlers ━━━
    const handleStartEditName = useCallback(() => {
        setIsEditingName(true);
        setEditedName(device?.name || device?.deviceId || '');
    }, [device?.name, device?.deviceId]);

    const handleCancelEditName = useCallback(() => {
        setEditedName(device?.name || device?.deviceId || '');
        setIsEditingName(false);
    }, [device?.name, device?.deviceId]);

    const handleSaveName = useCallback(async () => {
        if (!device || !editedName.trim()) return;

        setIsSavingName(true);
        try {
            const updatedDevice = await updateDeviceName(
                device.deviceId,
                editedName
            );
            setDevice(updatedDevice);
            setIsEditingName(false);
            setSnackbar({
                open: true,
                message: 'Gerätename erfolgreich aktualisiert',
                severity: 'success'
            });
        } catch (err) {
            console.error('Failed to rename:', err);
            setSnackbar({
                open: true,
                message: 'Fehler beim Umbenennen des Geräts',
                severity: 'error'
            });
        } finally {
            setIsSavingName(false);
        }
    }, [device]);

    // ━━━ Temperature Handlers ━━━
    const handleSliderChange = useCallback(
        (event: Event, newValue: number | number[]) => {
            const temp = newValue as number;
            if (validateTemperature(temp)) {
                setTargetTemp(temp);
            }
        },
        [validateTemperature]
    );

    const handleEnterManualMode = useCallback(() => {
        setManualTempValue(targetTemp.toString());
        setManualInputError(null);
        setIsManualInput(true);
    }, [targetTemp]);

    const handleCancelManualMode = useCallback(() => {
        setIsManualInput(false);
        setManualInputError(null);
    }, []);

    const handleSaveManualMode = useCallback(() => {
        const val = parseFloat(manualTempValue);

        if (isNaN(val)) {
            setManualInputError('Ungültige Zahl');
            return;
        }

        if (!validateTemperature(val)) {
            setManualInputError(`Bereich: ${GLOBAL_MIN}°C bis ${GLOBAL_MAX}°C`);
            return;
        }

        setTargetTemp(val);
        updateSliderBounds(val);
        setIsManualInput(false);
        setManualInputError(null);
    }, [manualTempValue, validateTemperature, updateSliderBounds]);

    const handleSendTemperature = useCallback(async () => {
        if (!device) return;

        if (!validateTemperature(targetTemp)) {
            setSnackbar({
                open: true,
                message: 'Ungültige Temperatur',
                severity: 'error'
            });
            return;
        }

        setIsSendingCommand(true);
        try {
            await sendTemperatureCommand(device.deviceId, targetTemp);
            setSnackbar({
                open: true,
                message: 'Befehl gesendet! Das Gerät wird aktualisiert, sobald es online ist.',
                severity: 'success'
            });
        } catch (err) {
            console.error('Command failed:', err);
            setSnackbar({
                open: true,
                message:
                    'Fehler: Keine Verbindung zum Broker. Versuchen Sie es später erneut.',
                severity: 'error'
            });
        } finally {
            setIsSendingCommand(false);
        }
    }, [device, targetTemp, validateTemperature]);

    const handleCloseSnackbar = useCallback(() => {
        setSnackbar(null);
    }, []);

    // ━━━ Computed Values ━━━
    const isSocketActive = wsStatus === 'connected';
    const showOfflineAlert = !isSocketActive || isDataStale;
    const effectiveTarget = liveData?.targetTemperature ?? targetTemp;
    const isHeating = liveData?.heatingStatus && (liveData.currentTemperature < (effectiveTarget - 0.1));
    const isCooling = liveData?.heatingStatus && (liveData.currentTemperature > (effectiveTarget + 0.1));

    const offlineMessage = !isSocketActive
        ? 'Verbindung unterbrochen'
        : 'Keine Daten vom Gerät';

    // ━━━ Render ━━━
    if (loading) {
        return (
            <Box p={3} display="flex" justifyContent="center">
                <CircularProgress />
            </Box>
        );
    }

    if (error || !device) {
        return (
            <Box p={3}>
                <Typography color="error">
                    {error || 'Gerät nicht gefunden'}
                </Typography>
            </Box>
        );
    }

    return (
        <Box sx={{ width: '100%' }}>
            {/* Breadcrumbs */}
            <Box sx={{ mb: 3 }}>
                <Breadcrumbs
                    separator={<NavigateNextIcon fontSize="small" />}
                    aria-label="breadcrumb"
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
                {/* Device Info */}
                <Grid size={{ xs: 12, md: 6 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            p: 3,
                            border: `1px solid ${theme.palette.divider}`,
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
                                    {isSavingName ? (
                                        <CircularProgress size={20} />
                                    ) : (
                                        <CheckIcon />
                                    )}
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
                        <Typography
                            variant="body1"
                            sx={{
                                fontFamily: 'monospace',
                                bgcolor: '#f5f5f5',
                                p: 1,
                                borderRadius: 1
                            }}
                        >
                            {device.deviceId}
                        </Typography>
                    </Paper>
                </Grid>

                {/* Temperature Control */}
                <Grid size={{ xs: 12, md: 6 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            p: 3,
                            border: `1px solid ${theme.palette.divider}`,
                            borderRadius: 2,
                            height: '100%'
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
                                        Geben Sie einen Wert zwischen {GLOBAL_MIN} und {GLOBAL_MAX}{' '}
                                        ein:
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
                                                endAdornment: (
                                                    <InputAdornment position="end">°C</InputAdornment>
                                                )
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

                                    <Stack
                                        spacing={2}
                                        direction="row"
                                        alignItems="center"
                                        sx={{ mt: 3 }}
                                    >
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
                                        startIcon={
                                            isSendingCommand ? (
                                                <CircularProgress size={20} color="inherit" />
                                            ) : (
                                                <SendIcon />
                                            )
                                        }
                                        onClick={handleSendTemperature}
                                        disabled={isSendingCommand}
                                        fullWidth
                                        sx={{ mt: 3, boxShadow: 'none', fontWeight: 600 }}
                                    >
                                        {isSendingCommand
                                            ? 'Senden...'
                                            : 'Temperatur Senden'}
                                    </Button>
                                </>
                            )}
                        </Box>
                    </Paper>
                </Grid>

                {/* Analytics */}
                <Grid size={{ xs: 12 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            p: 3,
                            border: `1px solid ${theme.palette.divider}`,
                            borderRadius: 2,
                            minHeight: 400,
                            overflow: 'hidden'
                        }}
                    >
                        <Box
                            sx={{
                                p: 2,
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center'
                            }}
                        >
                            <Typography variant="h6" fontWeight="bold">
                                Echtzeit-Analyse
                            </Typography>
                            {isSocketActive && liveData && !isDataStale ? (
                                <Chip
                                    icon={
                                        <FiberManualRecordIcon
                                            sx={{
                                                fontSize: '10px !important',
                                                color: '#4caf50',
                                                animation: 'pulse 1.5s infinite'
                                            }}
                                        />
                                    }
                                    label="LIVE"
                                    size="small"
                                    variant="outlined"
                                    sx={{
                                        fontWeight: 'bold',
                                        borderColor: '#4caf50',
                                        color: '#2e7d32',
                                        bgcolor: 'rgba(76, 175, 80, 0.1)',
                                        '@keyframes pulse': {
                                            '0%': { opacity: 1 },
                                            '50%': { opacity: 0.5 },
                                            '100%': { opacity: 1 }
                                        }
                                    }}
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
                                {/* KPIs */}
                                <Grid size={{ xs: 12, md: 3 }}>
                                    <Stack spacing={4}>
                                        {/* Current Temperature */}
                                        <Box>
                                            <Typography
                                                variant="caption"
                                                color="text.secondary"
                                                fontWeight="bold"
                                                letterSpacing={1}
                                            >
                                                AKTUELLE TEMP
                                            </Typography>
                                            <Typography
                                                variant="h2"
                                                color="primary.main"
                                                fontWeight="bold"
                                                sx={{ mt: 1 }}
                                            >
                                                {liveData ? (
                                                    <>
                                                        {liveData.currentTemperature.toFixed(1)}
                                                        <Typography
                                                            component="span"
                                                            variant="h5"
                                                            color="text.secondary"
                                                            sx={{ ml: 1 }}
                                                        >
                                                            °C
                                                        </Typography>
                                                    </>
                                                ) : showOfflineAlert ? (
                                                    '--.-'
                                                ) : (
                                                    <CircularProgress
                                                        size={40}
                                                        thickness={5}
                                                        sx={{ mt: 1 }}
                                                    />
                                                )}
                                            </Typography>
                                        </Box>

                                        {/* Status */}
                                        <Box>
                                            <Typography
                                                variant="caption"
                                                color="text.secondary"
                                                fontWeight="bold"
                                                letterSpacing={1}
                                            >
                                                STATUS
                                            </Typography>
                                            <Box sx={{ mt: 1 }}>
                                                {liveData ? (
                                                    <Chip
                                                        icon={
                                                            isHeating ? (
                                                                <WhatshotIcon />
                                                            ) : isCooling ? (
                                                                <AcUnitIcon />
                                                            ) : undefined
                                                        }
                                                        label={
                                                            isHeating
                                                                ? 'HEIZT AUF'
                                                                : isCooling
                                                                    ? 'KÜHLT AB'
                                                                    : 'IDLE'
                                                        }
                                                        color={
                                                            isHeating
                                                                ? 'error'
                                                                : isCooling
                                                                    ? 'info'
                                                                    : 'default'
                                                        }
                                                        variant={
                                                            isHeating || isCooling
                                                                ? 'filled'
                                                                : 'outlined'
                                                        }
                                                        sx={{
                                                            fontWeight: 'bold',
                                                            px: 1,
                                                            height: 32
                                                        }}
                                                    />
                                                ) : showOfflineAlert ? (
                                                    ''
                                                ) : (
                                                    <CircularProgress size={20} color="inherit" />
                                                )}
                                            </Box>
                                        </Box>
                                    </Stack>
                                </Grid>
                                <Grid size={{ xs: 12, md: 9 }}>
                                    <TemperatureChart
                                        data={historyData}
                                        height={350}
                                        loading={!liveData && historyData.length === 0}
                                        isOffline={showOfflineAlert}
                                        emptyMessage="Sammle erste Datenpunkte..."
                                        offlineMessage={offlineMessage}
                                    />
                                </Grid>
                            </Grid>
                        </Box>
                    </Paper>
                </Grid>
            </Grid>

            {/* Snackbar */}
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
        </Box>
    );
};

export default DeviceDetailPage;