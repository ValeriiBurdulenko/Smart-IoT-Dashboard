import React, { useState, useEffect } from 'react';
import {
    Box, Typography, Paper, Stack, IconButton, Pagination,
    Switch, FormControlLabel, Chip, Button, Tooltip, CircularProgress, Link
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { format } from 'date-fns';

// Icons for Actions
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import NotificationsOffIcon from '@mui/icons-material/NotificationsOff';

// Icons for Alert Types
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'; // Generic Info
import BugReportIcon from '@mui/icons-material/BugReport';       // MALFUNCTION
import SecurityIcon from '@mui/icons-material/Security';         // SECURITY_SPAM
import TrendingDownIcon from '@mui/icons-material/TrendingDown'; // CRITICAL_DIRECTION
import HourglassDisabledIcon from '@mui/icons-material/HourglassDisabled'; // STUCK
import CloudOffIcon from '@mui/icons-material/CloudOff';         // OFFLINE

import { getAlerts, markAlertAsRead, deleteAlert, markAllAlertsAsRead } from '../services/ApiService';
import type { Alert, AlertType } from '../types';

const ITEMS_PER_PAGE = 10;

const AlertsPage: React.FC = () => {
    const [alerts, setAlerts] = useState<Alert[]>([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(1);
    const [totalPages, setTotalPages] = useState(1);
    const [unreadOnly, setUnreadOnly] = useState(false);

    const [processingIds, setProcessingIds] = useState<Set<string>>(new Set());

    const fetchAlerts = () => {
        setLoading(true);
        getAlerts(page - 1, ITEMS_PER_PAGE, unreadOnly)
            .then(data => {
                setAlerts(data.content);
                setTotalPages(data.totalPages);
            })
            .catch(console.error)
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        fetchAlerts();
    }, [page, unreadOnly]);

    // Handlers
    const handleMarkAsRead = async (alertId: string) => {
        setProcessingIds(prev => new Set(prev).add(alertId));
        try {
            await markAlertAsRead(alertId);
            setAlerts(prev => prev.map(a => a.alertId === alertId ? { ...a, read: true } : a));
        } finally {
            setProcessingIds(prev => {
                const next = new Set(prev);
                next.delete(alertId);
                return next;
            });
        }
    };

    const handleDelete = async (alertId: string) => {
        setProcessingIds(prev => new Set(prev).add(alertId));
        try {
            await deleteAlert(alertId);
            setAlerts(prev => prev.filter(a => a.alertId !== alertId));
        } finally {
            setProcessingIds(prev => {
                const next = new Set(prev);
                next.delete(alertId);
                return next;
            });
        }
    };

    const handleMarkAllRead = async () => {
        setLoading(true);
        await markAllAlertsAsRead();
        fetchAlerts();
    };

    // --- VISUAL HELPERS ---

    const getAlertConfig = (type: AlertType) => {
        switch (type) {
            case 'MALFUNCTION':
                return {
                    icon: <BugReportIcon color="error" />,
                    label: 'Gerätefehler',
                    color: '#d32f2f'
                };
            case 'SECURITY_SPAM':
                return {
                    icon: <SecurityIcon color="error" />,
                    label: 'Sicherheitswarnung (Spam)',
                    color: '#d32f2f'
                };
            case 'CRITICAL_DIRECTION':
                return {
                    icon: <TrendingDownIcon color="error" />,
                    label: 'Kritische Temperaturabweichung',
                    color: '#d32f2f'
                };
            case 'STUCK':
                return {
                    icon: <HourglassDisabledIcon color="warning" />,
                    label: 'Temperatur stagniert',
                    color: '#ed6c02'
                };
            case 'OFFLINE':
                return {
                    icon: <CloudOffIcon color="warning" />,
                    label: 'Gerät Offline',
                    color: '#ed6c02'
                };
            default:
                return {
                    icon: <InfoOutlinedIcon color="info" />,
                    label: 'Information',
                    color: '#0288d1'
                };
        }
    };

    return (
        <Box sx={{ width: '100%' }}>
            {/* Header */}
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                <Typography variant="h4" fontWeight="bold">Benachrichtigungen</Typography>

                <Stack direction="row" spacing={2}>
                    <FormControlLabel
                        control={
                            <Switch
                                checked={unreadOnly}
                                onChange={(e) => { setUnreadOnly(e.target.checked); setPage(1); }}
                            />
                        }
                        label="Nur ungelesene"
                    />
                    <Button
                        variant="outlined"
                        startIcon={<DoneAllIcon />}
                        onClick={handleMarkAllRead}
                        disabled={alerts.length === 0}
                    >
                        Alle lesen
                    </Button>
                </Stack>
            </Box>

            {/* List */}
            <Stack spacing={2}>
                {loading ? (
                    <Box sx={{ display: 'flex', justifyContent: 'center', py: 5 }}>
                        <CircularProgress />
                    </Box>
                ) : alerts.length === 0 ? (
                    <Paper
                        elevation={0}
                        sx={{
                            p: 5, textAlign: 'center', bgcolor: '#f9f9f9',
                            border: '1px dashed #ccc', borderRadius: 2
                        }}
                    >
                        <NotificationsOffIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 2 }} />
                        <Typography color="text.secondary">Keine Benachrichtigungen vorhanden</Typography>
                    </Paper>
                ) : (
                    alerts.map((alert) => {
                        const config = getAlertConfig(alert.type);

                        return (
                            <Paper
                                key={alert.alertId}
                                elevation={0}
                                sx={{
                                    p: 2,
                                    display: 'flex',
                                    alignItems: 'center',
                                    border: '1px solid',
                                    borderColor: 'divider',
                                    borderLeft: alert.read
                                        ? '1px solid #e0e0e0'
                                        : `4px solid ${config.color}`,
                                    bgcolor: alert.read ? 'background.paper' : '#f8fdff',
                                    transition: 'all 0.2s',
                                    opacity: processingIds.has(alert.alertId) ? 0.5 : 1
                                }}
                            >
                                {/* Icon */}
                                <Box sx={{ mr: 2, display: 'flex', alignItems: 'center' }}>
                                    {config.icon}
                                </Box>

                                {/* Content */}
                                <Box sx={{ flexGrow: 1 }}>
                                    <Stack direction="row" alignItems="center" spacing={1} mb={0.5}>
                                        <Typography variant="subtitle1" fontWeight={alert.read ? 'normal' : 'bold'}>
                                            {config.label}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            • {format(new Date(alert.timestamp), 'dd.MM.yyyy HH:mm:ss')}
                                        </Typography>
                                        {!alert.read && (
                                            <Chip label="NEU" size="small" color="primary" sx={{ height: 20, fontSize: '0.65rem' }} />
                                        )}
                                    </Stack>

                                    <Typography variant="body2" color="text.primary">
                                        {alert.message}
                                    </Typography>

                                    <Typography variant="caption" sx={{ mt: 1, display: 'block' }}>
                                        Gerät: <Link component={RouterLink} to={`/devices/${alert.deviceId}`} underline="hover">{alert.deviceId}</Link>
                                        {' '}| Wert: <strong>{alert.value.toFixed(1)}</strong>
                                    </Typography>
                                </Box>

                                {/* Actions */}
                                <Stack direction="row" spacing={1}>
                                    {!alert.read && (
                                        <Tooltip title="Als gelesen markieren">
                                            <IconButton
                                                color="primary"
                                                onClick={() => handleMarkAsRead(alert.alertId)}
                                            >
                                                <CheckCircleOutlineIcon />
                                            </IconButton>
                                        </Tooltip>
                                    )}
                                    <Tooltip title="Löschen">
                                        <IconButton
                                            color="error"
                                            onClick={() => handleDelete(alert.alertId)}
                                        >
                                            <DeleteIcon />
                                        </IconButton>
                                    </Tooltip>
                                </Stack>
                            </Paper>
                        );
                    })
                )}
            </Stack>

            {/* Pagination */}
            {totalPages > 1 && (
                <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
                    <Pagination
                        count={totalPages}
                        page={page}
                        onChange={(e, v) => setPage(v)}
                        color="primary"
                    />
                </Box>
            )}
        </Box>
    );
};

export default AlertsPage;