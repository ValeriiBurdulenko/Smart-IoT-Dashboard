import React, { useState, useEffect } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import {
    Box, Typography, Breadcrumbs, Link, Paper, Grid,
    TextField, IconButton, Button, Stack, Divider, InputAdornment, Slider, Tooltip
} from '@mui/material';

// Иконки
import EditIcon from '@mui/icons-material/Edit';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import SendIcon from '@mui/icons-material/Send';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import AnalyticsIcon from '@mui/icons-material/BarChart';
import KeyboardIcon from '@mui/icons-material/Keyboard';

// Тип для девайса (импортируем или используем локально пока)
import type { Device } from '../types';
import { getDevices } from '../services/ApiService'; // Чтобы найти девайс по ID

const DeviceDetailPage: React.FC = () => {
    const { id: deviceId } = useParams<{ id: string }>();

    // --- STATE (пока локальный для UI-теста) ---
    const [device, setDevice] = useState<Device | null>(null);
    const [loading, setLoading] = useState(true);

    // Для редактирования имени
    const [isEditingName, setIsEditingName] = useState(false);
    const [editedName, setEditedName] = useState("");

    // Для отправки команды температуры
    const [targetTemp, setTargetTemp] = useState<number>(22);
    const [isManualInput, setIsManualInput] = useState(false);
    const [manualTempValue, setManualTempValue] = useState<string>("22");

    const [sliderBounds, setSliderBounds] = useState({ min: 10, max: 30 });

    // Имитация загрузки данных (завтра подключим реальный GET /devices/{id})
    useEffect(() => {
        // Временно ищем в общем списке, пока нет отдельного эндпоинта
        getDevices().then(devices => {
            const found = devices.find(d => d.deviceId === deviceId);
            if (found) {
                setDevice(found);
                setEditedName(found.name || found.deviceId);
            }
            setLoading(false);
        });
    }, [deviceId]);

    // --- HANDLERS ---

    const handleStartEditName = () => {
        setIsEditingName(true);
        // Если имя было пустым, подставляем ID
        setEditedName(device?.name || device?.deviceId || "");
    };

    const handleSaveName = () => {
        // TODO: Завтра здесь будет API запрос: PUT /devices/{id} { name: ... }
        console.log("Saving new name:", editedName);

        // Оптимистичное обновление UI
        if (device) {
            setDevice({ ...device, name: editedName });
        }
        setIsEditingName(false);
    };

    const handleCancelEditName = () => {
        // Сброс к старому имени
        setEditedName(device?.name || device?.deviceId || "");
        setIsEditingName(false);
    };

    const handleSendTemperature = () => {
        // TODO: Завтра здесь будет API запрос: POST /devices/{id}/command
        console.log(`Sending command: set_target_temp = ${targetTemp}`);
        alert(`Команда отправлена: Установить ${targetTemp}°C`);
    };

    // Слайдер изменился
    const handleSliderChange = (event: Event, newValue: number | number[]) => {
        setTargetTemp(newValue as number);
    };

    // Вход в режим ручного ввода
    const handleEnterManualMode = () => {
        setManualTempValue(targetTemp.toString());
        setIsManualInput(true);
    };

    // Отмена ручного ввода
    const handleCancelManualMode = () => {
        setIsManualInput(false);
    };

    const GLOBAL_MIN = -40;
    const GLOBAL_MAX = 100;

    // Сохранение ручного ввода
    const handleSaveManualMode = () => {
        let val = parseFloat(manualTempValue);

        if (isNaN(val)) val = typeof targetTemp === 'number' ? targetTemp : 0;


        if (val < GLOBAL_MIN) val = GLOBAL_MIN;
        if (val > GLOBAL_MAX) val = GLOBAL_MAX;

        setTargetTemp(val);

        const newMin = Math.max(GLOBAL_MIN, Math.floor(val - 10));
        const newMax = Math.min(GLOBAL_MAX, Math.ceil(val + 10));

        setSliderBounds({
            min: newMin,
            max: newMax
        });

        setIsManualInput(false);
    };

    if (loading) return <Box p={3}>Laden...</Box>;
    if (!device) return <Box p={3}>Gerät nicht gefunden.</Box>;

    return (
        <Box sx={{ width: '100%' }}>

            {/* 1. Хлебные крошки (Навигация) */}
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

                {/* 2. Блок информации и Имя (Редактируемое) */}
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

                        {/* Логика переключения: Просмотр <-> Редактирование */}
                        {isEditingName ? (
                            // --- РЕЖИМ РЕДАКТИРОВАНИЯ ---
                            <Stack direction="row" spacing={1} alignItems="center" mt={1}>
                                <TextField
                                    fullWidth
                                    size="small"
                                    value={editedName}
                                    onChange={(e) => setEditedName(e.target.value)}
                                    autoFocus
                                />
                                <IconButton
                                    color="success"
                                    onClick={handleSaveName}
                                    sx={{ border: '1px solid', borderColor: 'success.light' }}
                                >
                                    <CheckIcon />
                                </IconButton>
                                <IconButton
                                    color="error"
                                    onClick={handleCancelEditName}
                                    sx={{ border: '1px solid', borderColor: 'error.light' }}
                                >
                                    <CloseIcon />
                                </IconButton>
                            </Stack>
                        ) : (
                            // --- РЕЖИМ ПРОСМОТРА ---
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

                {/* 3. Блок управления (Температура) */}
                <Grid size={{ xs: 12, md: 6 }}>
                    <Paper elevation={0} sx={{ p: 3, border: '1px solid #e0e0e0', borderRadius: 2, height: '100%' }}>

                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                            <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center' }}>
                                <ThermostatIcon sx={{ mr: 1, color: 'primary.main' }} />
                                Steuerung
                            </Typography>

                            {/* Кнопка переключения режима (только если мы не в режиме ввода) */}
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
                                // --- РЕЖИМ: РУЧНОЙ ВВОД ---
                                <Stack spacing={2}>
                                    <Typography variant="body2" color="text.secondary">
                                        Geben Sie einen Wert zwischen -40 und 100 ein:
                                    </Typography>
                                    <Stack direction="row" spacing={1}>
                                        <TextField
                                            type="number"
                                            fullWidth
                                            value={manualTempValue}
                                            onChange={(e) => setManualTempValue(e.target.value)}
                                            InputProps={{
                                                endAdornment: <InputAdornment position="end">°C</InputAdornment>,
                                                inputProps: { min: -40, max: 100 }
                                            }}
                                            autoFocus
                                        />
                                        <Button variant="contained" onClick={handleSaveManualMode}>OK</Button>
                                        <Button variant="outlined" color="error" onClick={handleCancelManualMode}>Cancel</Button>
                                    </Stack>
                                </Stack>
                            ) : (
                                // --- РЕЖИМ: СЛАЙДЕР ---
                                <>
                                    <Typography fontWeight="bold" color="primary.main" align="center" gutterBottom
                                        sx={{ fontSize: 32 }}
                                    >
                                        {targetTemp}°C
                                    </Typography>

                                    <Stack spacing={2} direction="row" alignItems="center" sx={{ mt: 3 }}>
                                        <Typography variant="caption" color="text.secondary">{sliderBounds.min}°</Typography>
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
                                        <Typography variant="caption" color="text.secondary">{sliderBounds.max}°</Typography>
                                    </Stack>

                                    <Button
                                        variant="contained"
                                        startIcon={<SendIcon />}
                                        onClick={handleSendTemperature}
                                        fullWidth
                                        sx={{ mt: 3, boxShadow: 'none', fontWeight: 600 }}
                                    >
                                        Temperatur Setzen
                                    </Button>
                                </>
                            )}
                        </Box>
                    </Paper>
                </Grid>

                {/* 4. График (Заглушка) */}
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

                        {/* Плейсхолдер для графика */}
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
        </Box>
    );
};

export default DeviceDetailPage;