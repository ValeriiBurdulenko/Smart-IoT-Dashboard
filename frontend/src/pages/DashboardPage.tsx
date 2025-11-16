import React from 'react';
import {
    Typography,
    Grid,
    Paper
} from '@mui/material';


const DashboardPage: React.FC = () => {

    return (
        <Grid container spacing={3}>

            {/* Haupt-Diagramm */}
            <Grid size={{ xs: 12 }}>
                <Paper elevation={0} sx={{ border: '1px solid #e0e0e0', p: 2, height: 400 }}>
                    <Typography variant="h6" sx={{ color: 'text.primary' }}>
                        Temperaturverlauf
                    </Typography>
                    {/* Hier kommt dein Chart-Komponente rein */}
                </Paper>
            </Grid>

            {/* Kleinere Karten */}
            <Grid size={{ xs: 12, md: 4 }}>
                <Paper elevation={0} sx={{ border: '1px solid #e0e0e0', p: 2, height: 240 }}>
                    <Typography variant="h6">Gerätestatus</Typography>
                    {/* Hier kommen deine Status-Daten rein */}
                </Paper>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
                <Paper elevation={0} sx={{ border: '1px solid #e0e0e0', p: 2, height: 240 }}>
                    <Typography variant="h6">Aktuelle Alarme</Typography>
                </Paper>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
                <Paper elevation={0} sx={{ border: '1px solid #e0e0e0', p: 2, height: 240 }}>
                    <Typography variant="h6">Luftfeuchtigkeit</Typography>
                </Paper>
            </Grid>

        </Grid>
    );
};

export default DashboardPage;