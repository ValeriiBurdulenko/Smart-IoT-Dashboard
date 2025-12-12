import React, { useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import DevicesPage from './pages/DevicesPage';
import DeviceDetailPage from './pages/DeviceDetailPage';
import WebSocketService from './services/WebSocketService';

const App: React.FC = () => {
    useEffect(() => {
        WebSocketService.activate();

        return () => {
            WebSocketService.deactivate();
        };
    }, []);
    return (
        <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
                path="/"
                element={
                    <ProtectedRoute>
                        <Layout />
                    </ProtectedRoute>
                }
            >
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="/" element={<Navigate to="/dashboard" replace />} />
                <Route path="dashboard" element={<DashboardPage />} />
                <Route path="devices" element={<DevicesPage />} />
                <Route path="devices/:id" element={<DeviceDetailPage />} />
                {/* <Route path="analyse" element={<AnalysePage />} /> */}
                {/* <Route path="alerts" element={<AlertsPage />} /> */}
            </Route>

            <Route path="*" element={<div>404 - Page Not Found</div>} />
        </Routes >
    );
};

export default App;