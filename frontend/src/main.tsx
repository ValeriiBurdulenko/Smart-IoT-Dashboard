import { StrictMode, useMemo } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.tsx';
import KeycloakService from './services/KeycloakService.js';
import { BrowserRouter } from 'react-router-dom';

import { ThemeProvider, CssBaseline } from '@mui/material';
import { lightTheme } from './theme';

import './index.css';
import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';

const root = createRoot(document.getElementById('root') as HTMLElement);

// Render Loading...
root.render(
  <StrictMode>
    <div>Loading Keycloak... Please wait...</div>
  </StrictMode>
);

// Initialize Keycloak
KeycloakService.initKeycloak()
    .then(() => {
      root.render(
        <StrictMode>
          <ThemeProvider theme={lightTheme}>
            <CssBaseline />
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </ThemeProvider>
        </StrictMode>
      );
    })
    .catch((error) => {
        console.error("Keycloak init failed:", error);
        root.render(
          <StrictMode>
            <div>Error: Could not initialize Keycloak.</div>
          </StrictMode>
        );
    });