import React, { useState, useEffect } from 'react';
import {
    Modal, Box, Typography, Button, CircularProgress, Alert
} from '@mui/material';
import { generateClaimCode } from '../services/ApiService';

const style = {
    position: 'absolute' as 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    width: 400,
    bgcolor: 'background.paper',
    border: '1px solid #e0e0e0',
    borderRadius: '8px',
    boxShadow: 24,
    p: 4,
    textAlign: 'center'
};

interface Props {
    open: boolean;
    onClose: () => void;
    onDeviceClaimed: () => void;
}

const AddDeviceModal: React.FC<Props> = ({ open, onClose, onDeviceClaimed }) => {
    const [isLoading, setIsLoading] = useState(false);
    const [claimCode, setClaimCode] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!open) {
            setClaimCode(null);
            setError(null);
            setIsLoading(false);
        }
    }, [open]);

    const handleGenerateCode = () => {
        setIsLoading(true);
        setError(null);
        setClaimCode(null);

        generateClaimCode()
            .then(response => {
                setClaimCode(response.claimCode);
            })
            .catch(err => {
                console.error("Error generating claim code:", err);
                setError("Fehler beim Generieren des Codes. Versuchen Sie es erneut.");
            })
            .finally(() => {
                setIsLoading(false);
            });
    };

    const handleClose = () => {
        onClose();
    };

    return (
        <Modal open={open} onClose={handleClose}>
            <Box sx={style}>
                <Typography variant="h5" component="h2" gutterBottom>
                    Gerät hinzufügen
                </Typography>

                {isLoading && <CircularProgress sx={{ my: 2 }} />}
                {error && <Alert severity="error" sx={{ my: 2 }}>{error}</Alert>}

                {claimCode && (
                    <Box sx={{ my: 2 }}>
                        <Typography variant="body1">
                            Öffnen Sie die Provisioning-Webseite Ihres Geräts (z.B. http://localhost:9090) und geben Sie diesen Code ein:
                        </Typography>
                        <Typography variant="h4" sx={{ my: 2, fontWeight: 'bold', letterSpacing: '0.1em', color: 'primary.main' }}>
                            {claimCode}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            Dieser Code ist 5 Minuten gültig.
                        </Typography>
                        <Button variant="contained" onClick={onDeviceClaimed} sx={{ mt: 3, width: '100%' }}>
                            Ich habe das Gerät verbunden
                        </Button>
                    </Box>
                )}

                {!claimCode && !isLoading && (
                    <Button variant="contained" onClick={handleGenerateCode} sx={{ mt: 2, width: '100%' }}>
                        PIN-Code generieren
                    </Button>
                )}
            </Box>
        </Modal>
    );
};

export default AddDeviceModal;