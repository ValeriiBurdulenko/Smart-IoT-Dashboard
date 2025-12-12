import React from 'react';
import { Navigate } from 'react-router-dom';
import KeycloakService from '../services/KeycloakService';

interface Props {
    children: React.ReactElement;
}

const ProtectedRoute: React.FC<Props> = ({ children }) => {
    const isLoggedIn = KeycloakService.isLoggedIn();

    if (!isLoggedIn) {
        return <Navigate to="/login" replace />;
    }

    return children;
};

export default ProtectedRoute;