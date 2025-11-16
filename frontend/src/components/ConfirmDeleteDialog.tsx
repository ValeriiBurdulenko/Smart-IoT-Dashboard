import React from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle
} from '@mui/material';

interface Props {
    open: boolean;
    onClose: () => void;
    onConfirm: () => void;
    deviceName: string;
}

const ConfirmDeleteDialog: React.FC<Props> = ({ open, onClose, onConfirm, deviceName }) => {
    return (
        <Dialog
            open={open}
            onClose={onClose}
            aria-labelledby="alert-dialog-title"
            aria-describedby="alert-dialog-description"
        >
            <DialogTitle id="alert-dialog-title">
                Gerät löschen?
            </DialogTitle>
            <DialogContent>
                <DialogContentText id="alert-dialog-description">
                    Bist du sicher, dass du das Gerät "{deviceName}" endgültig löschen willst? Diese Aktion kann nicht rückgängig gemacht werden.
                </DialogContentText>
            </DialogContent>
            <DialogActions sx={{ p: 2 }}>
                {/* "Abbrechen"-Button */}
                <Button onClick={onClose} color="primary" variant="outlined">
                    Abbrechen
                </Button>
                {/* "Löschen"-Button (Rot für Gefahr) */}
                <Button onClick={onConfirm} color="error" variant="contained" autoFocus>
                    Löschen
                </Button>
            </DialogActions>
        </Dialog>
    );
};

export default ConfirmDeleteDialog;