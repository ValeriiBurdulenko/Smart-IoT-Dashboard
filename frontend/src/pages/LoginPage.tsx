import React from 'react';
import KeycloakService from '../services/KeycloakService';

const LoginPage: React.FC = () => {
    return (
        <div>
            <h1>IoT Dashboard Login</h1>
            <p>You must be logged in to see your devices.</p>
            <button onClick={() => KeycloakService.login()}>
                Login with Keycloak
            </button>
        </div>
    );
};

export default LoginPage;