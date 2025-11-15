import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL,
    realm: import.meta.env.VITE_KEYCLOAK_REALM,
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID
});


const initKeycloak = (): Promise<boolean> => {
    return keycloak.init({
        onLoad: 'login-required',
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
        pkceMethod: 'S256',
        checkLoginIframe: false
    });
};

const login = () => keycloak.login();
const logout = () => keycloak.logout();
const getToken = () => keycloak.token;
const isLoggedIn = () => !!keycloak.token;
const getUsername = () => keycloak.tokenParsed?.given_name || keycloak.tokenParsed?.preferred_username || "User";
const getUserId = () => keycloak.subject;

const KeycloakService = {
    initKeycloak,
    login,
    logout,
    getToken,
    isLoggedIn,
    getUsername,
    getUserId,
    keycloakInstance: keycloak
};

export default KeycloakService;