import React, { useState, useEffect } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
    Box, Typography, Button, Paper, Stack, IconButton, Pagination,
    Switch, Link, CircularProgress
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/DeleteForever';

import { getDevices, deleteDevice } from '../services/ApiService';
import type { Device } from '../types';

import AddDeviceModal from '../components/AddDeviceModal';
import ConfirmDeleteDialog from '../components/ConfirmDeleteDialog';

const ITEMS_PER_PAGE = 5;

const DevicesPage: React.FC = () => {
    const [devices, setDevices] = useState<Device[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [page, setPage] = useState(1);
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
    const [deviceToDelete, setDeviceToDelete] = useState<Device | null>(null);
    const [isDeleting, setIsDeleting] = useState(false);

    const fetchDevices = () => {
        setLoading(true);
        getDevices()
            .then(data => setDevices(data))
            .catch(err => {
                console.error("Error fetching devices:", err);
                setError("Failed to load devices.");
            })
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        fetchDevices();
    }, []);

    const handleOpenDeleteDialog = (device: Device) => {
        setDeviceToDelete(device);
        setIsDeleteModalOpen(true);
    };

    const handleCloseDeleteDialog = () => {
        if (!isDeleting) {
            setDeviceToDelete(null);
            setIsDeleteModalOpen(false);
        }
    };

    const handleConfirmDelete = () => {
        if (!deviceToDelete) return;

        setIsDeleting(true);

        deleteDevice(deviceToDelete.deviceId)
            .then(() => {
                fetchDevices();
                setDeviceToDelete(null);
                setIsDeleteModalOpen(false);
            })
            .catch(err => {
                console.error("Error deleting device:", err);
                setError("Failed to delete device.");
                setIsDeleteModalOpen(false);
            })
            .finally(() => setIsDeleting(false));
    };

    // Paginierungs-Logik
    const activeDevices = devices.filter(device => device.active === true);
    const pageCount = Math.ceil(activeDevices.length / ITEMS_PER_PAGE);
    const paginatedDevices = activeDevices.slice(
        (page - 1) * ITEMS_PER_PAGE,
        page * ITEMS_PER_PAGE
    );

    return (
        <Box sx={{ width: '100%', maxWidth: '100%' }}>
            {/* Header: Titel und Button */}
            <Box sx={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                width: '100%',
                mb: 4,
                mt: 1
            }}>
                <Typography variant="h4">Geräte</Typography>
                <Button variant="contained" startIcon={<AddIcon />} onClick={() => setIsAddModalOpen(true)}>
                    Gerät hinzufügen
                </Button>
            </Box>

            {/* Geräteliste */}
            <Stack spacing={2}>
                {loading && <CircularProgress sx={{ margin: 'auto' }} />}
                {error && <Typography color="error">{error}</Typography>}

                {/* 3. Wir mappen jetzt 'paginatedDevices' (nur aktive) */}
                {!loading && paginatedDevices.map((device) => (
                    <Paper
                        key={device.deviceId}
                        elevation={0}
                        sx={{
                            p: 2, display: 'flex', alignItems: 'center',
                            border: '1px solid #e0e0e0',
                            '&:hover': { bgcolor: 'action.hover' }
                        }}
                    >
                        {/* Dieser Punkt wird jetzt IMMER grün sein (da device.isActive immer true ist) */}
                        <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: 'success.main', mr: 2 }} />

                        <Box sx={{ flexGrow: 1 }}>
                            <Link component={RouterLink} to={`/devices/${device.deviceId}`} sx={{ color: 'text.primary', fontWeight: 'bold', fontSize: '1.1rem' }} underline="hover">
                                {device.name || device.deviceId}
                            </Link>
                            <Typography variant="body2" color="text.secondary">
                                ID: {device.deviceId}
                            </Typography>
                        </Box>

                        <Box>
                            {/* Dieser Switch ist jetzt immer 'checked=true' und 'disabled'.
                                Das ist OK, da es ein Platzhalter für die zukünftige Funktion
                                "temporär deaktivieren" ist (was etwas anderes ist als "löschen").
                            */}
                            <Switch
                                checked={device.active}
                                disabled
                                title="Aktiv/Deaktiv (Zukünftige Funktion)"
                            />
                            <IconButton
                                color="error"
                                title="Gerät löschen"
                                onClick={() => handleOpenDeleteDialog(device)}
                            >
                                <DeleteIcon />
                            </IconButton>
                        </Box>
                    </Paper>
                ))}
            </Stack>

            {/* Paginierung */}
            {pageCount > 1 && (
                <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
                    <Pagination
                        count={pageCount}
                        page={page}
                        onChange={(e, value) => setPage(value)}
                        color="primary"
                    />
                </Box>
            )}

            {/* Modals */}
            <AddDeviceModal
                open={isAddModalOpen}
                onClose={() => setIsAddModalOpen(false)}
                onDeviceClaimed={() => {
                    setIsAddModalOpen(false);
                    fetchDevices();
                }}
            />
            {deviceToDelete && (
                <ConfirmDeleteDialog
                    open={isDeleteModalOpen}
                    onClose={handleCloseDeleteDialog}
                    onConfirm={handleConfirmDelete}
                    deviceName={deviceToDelete.name || deviceToDelete.deviceId}
                    isLoading={isDeleting}
                />
            )}
        </Box>
    );
};

export default DevicesPage;