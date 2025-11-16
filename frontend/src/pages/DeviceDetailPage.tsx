import React from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import { Box, Typography, Breadcrumbs, Link, Paper } from '@mui/material';

const DeviceDetailPage: React.FC = () => {
    // Ruft die ':id' aus der URL ab
    const { id: deviceId } = useParams<{ id: string }>();

    return (
        <Box>
            {/* Breadcrumbs für die Navigation */}
            <Breadcrumbs aria-label="breadcrumb" sx={{ mb: 2 }}>
                <Link component={RouterLink} underline="hover" color="inherit" to="/devices">
                    Geräte
                </Link>
                <Typography color="text.primary">{deviceId}</Typography>
            </Breadcrumbs>

            <Paper sx={{ p: 2 }}>
                <Typography variant="h4">Gerätedetails: {deviceId}</Typography>
                <Typography sx={{ mt: 2 }}>
                    Hier kommen die detaillierten Live-Diagramme und Einstellungen für dieses spezifische Gerät hin.
                </Typography>
            </Paper>
        </Box>
    );
};

export default DeviceDetailPage;