import React, { useEffect, useState } from 'react';
import { Snackbar, Alert as MuiAlert, Typography } from '@mui/material';
import WebSocketService from '../services/WebSocketService';
import type { AlertData } from '../services/WebSocketService';
import KeycloakService from '../services/KeycloakService';

const GlobalAlerts: React.FC = () => {
    const [open, setOpen] = useState(false);
    const [alert, setAlert] = useState<AlertData | null>(null);

    useEffect(() => {
        const userId = KeycloakService.getUserId();
        if (!userId) return;

        const sub = WebSocketService.subscribeToUserAlerts(userId, (newAlert) => {
            console.log("🔔 Global Alert Received:", newAlert);
            setAlert(newAlert);
            setOpen(true);
        });

        return () => sub.unsubscribe();
    }, []);

    const handleClose = (event?: React.SyntheticEvent | Event, reason?: string) => {
        if (reason === 'clickaway') return;
        setOpen(false);
    };

    if (!alert) return null;

    return (
        <Snackbar
            open={open}
            autoHideDuration={6000}
            onClose={handleClose}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        >
            <MuiAlert
                onClose={handleClose}
                severity={alert.type === 'CRITICAL' ? 'error' : 'warning'}
                variant="filled"
                sx={{ width: '100%', boxShadow: 3 }}
            >
                <Typography variant="subtitle2" fontWeight="bold">
                    {alert.type} @ {alert.deviceId.substring(0, 8)}...
                </Typography>
                {alert.message}
            </MuiAlert>
        </Snackbar>
    );
};

export default GlobalAlerts;