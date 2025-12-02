import React, { useState, useEffect } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import {
    Box, Typography, Breadcrumbs, Link, Paper, Grid,
    TextField, IconButton, Button, Stack, Divider, InputAdornment, Slider, Tooltip,
    CircularProgress, Snackbar, Alert
} from '@mui/material';

import EditIcon from '@mui/icons-material/Edit';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import SendIcon from '@mui/icons-material/Send';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import AnalyticsIcon from '@mui/icons-material/BarChart';
import KeyboardIcon from '@mui/icons-material/Keyboard';

import type { Device } from '../types';
import { getDeviceById, updateDeviceName, sendTemperatureCommand } from '../services/ApiService';

type SnackbarState = {
    open: boolean;
    message: string;
    severity: 'success' | 'error' | 'warning';
} | null;

const DeviceDetailPage: React.FC = () => {
    const { id: deviceId } = useParams<{ id: string }>();

    // --- STATE ---
    const [device, setDevice] = useState<Device | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [snackbar, setSnackbar] = useState<SnackbarState>(null);

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

    const GLOBAL_MIN = -40;
    const GLOBAL_MAX = 100;

    useEffect(() => {
        fetchDevice();
    }, [deviceId]);

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

    if (loading) return <Box p={3}><CircularProgress /></Box>;
    if (error || !device) return <Box p={3}><Typography color="error">{error || "Gerät nicht gefunden"}</Typography></Box>;

    return (
        <Box sx={{ width: '100%' }}>

            {/* Breadcrumbs */}
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

            <Grid container spacing={3}>

                {/* Device Info & Name */}
                <Grid size={{ xs: 12, md: 6 }}>
                    <Paper
                        elevation={0}
                        sx={{
                            p: 3,
                            border: '1px solid #e0e0e0',
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
                    <Paper elevation={0} sx={{ p: 3, border: '1px solid #e0e0e0', borderRadius: 2, height: '100%' }}>

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
                            border: '1px solid #e0e0e0',
                            borderRadius: 2,
                            minHeight: 400,
                            display: 'flex',
                            flexDirection: 'column'
                        }}
                    >
                        <Typography variant="h6">Live-Daten</Typography>

                        <Box
                            sx={{
                                flexGrow: 1,
                                mt: 2,
                                bgcolor: '#f9f9f9',
                                border: '1px dashed #ccc',
                                borderRadius: 2,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                flexDirection: 'column'
                            }}
                        >
                            <AnalyticsIcon sx={{ fontSize: 60, color: '#ddd', mb: 2 }} />
                            <Typography color="text.secondary">
                                Hier wird morgen der Live-Chart (Recharts/Chart.js) integriert.
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                                Datenquelle: Kafka/InfluxDB - WebSocket
                            </Typography>
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
        </Box>
    );
};

export default DeviceDetailPage;