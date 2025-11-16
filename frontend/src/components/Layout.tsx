import React, { useState } from 'react';
import KeycloakService from '../services/KeycloakService';
import {
    Box,
    CssBaseline,
    AppBar,
    Toolbar,
    Typography,
    Drawer,
    List,
    ListItem,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    IconButton,
    Menu,
    MenuItem
} from '@mui/material';

import DashboardIcon from '@mui/icons-material/Dashboard';
import DevicesIcon from '@mui/icons-material/Router';
import AnalyticsIcon from '@mui/icons-material/BarChart';
import NotificationsIcon from '@mui/icons-material/Notifications';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import SettingsIcon from '@mui/icons-material/Settings';
import LogoutIcon from '@mui/icons-material/Logout';
import MenuIcon from '@mui/icons-material/Menu';
import { NavLink, Outlet } from 'react-router-dom';

const drawerWidth = 280;

const Layout: React.FC = () => {

    const [mobileOpen, setMobileOpen] = useState(false);

    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
    const isMenuOpen = Boolean(anchorEl);

    const handleDrawerToggle = () => {
        setMobileOpen(!mobileOpen);
    };

    const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleMenuClose = () => {
        setAnchorEl(null);
    };

    const handleLogout = () => {
        handleMenuClose();
        KeycloakService.logout();
    };

    const navItems = [
        { text: 'Dashboard', icon: <DashboardIcon />, path: '/dashboard' },
        { text: 'Geräte (Devices)', icon: <DevicesIcon />, path: '/devices' },
        { text: 'Analyse', icon: <AnalyticsIcon />, path: '/analyse' },
        { text: 'Alarme (Alerts)', icon: <NotificationsIcon />, path: '/alerts' },
    ];

    const drawerContent = (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>

            {/* Top section: Navigation */}
            <Box sx={{ flexGrow: 1, overflow: 'auto' }}>
                <Toolbar>
                    <Typography variant="h6" noWrap component="div">
                        Smart IoT Dashboard
                    </Typography>
                </Toolbar>
                <List>
                    {navItems.map((item) => (
                        <ListItem key={item.text} disablePadding>
                            <ListItemButton
                                component={NavLink}
                                to={item.path}
                                sx={{
                                    color: 'white',
                                    "& .MuiListItemIcon-root": { color: 'white' },
                                    "&.active": {
                                        backgroundColor: 'rgba(255, 255, 255, 0.2)',
                                    },
                                    "&.Mui-selected": { backgroundColor: 'rgba(255, 255, 255, 0.2)' },
                                    "&.Mui-selected:hover": { backgroundColor: 'rgba(255, 255, 255, 0.3)' },
                                    "&:hover": { backgroundColor: 'rgba(255, 255, 255, 0.1)', color: 'white', }
                                }}
                            >
                                <ListItemIcon>{item.icon}</ListItemIcon>
                                <ListItemText primary={item.text} />
                            </ListItemButton>
                        </ListItem>
                    ))}
                </List>
            </Box>

            {/* Bottom section: User menu */}
            <Box sx={{
                p: 2,
                borderTop: '1px solid rgba(255, 255, 255, 0.12)',
                flexShrink: 0
            }}>
                <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'flex-end'
                }}>
                    <Typography
                        variant="body1"
                        sx={{ color: 'inherit', fontWeight: 500, mr: 1, fontSize: 20 }}
                    >
                        {KeycloakService.getUsername()}
                    </Typography>
                    <IconButton
                        aria-label="User settings"
                        onClick={handleMenuOpen}
                        sx={{
                            color: 'inherit',

                            // Diese Regel entfernt den "Kreis" (Fokus-Ring),
                            // der nach dem Schließen des Menüs hängen bleibt.
                            '&:focus': {
                                backgroundColor: 'transparent',
                                outline: 'none'
                            },
                            // (Sicherheitsnetz für :focus-visible)
                            '&:focus-visible': {
                                backgroundColor: 'transparent',
                                outline: 'none'
                            }
                        }}
                    >
                        <MoreVertIcon />
                    </IconButton>
                </Box>
            </Box>
        </Box>
    );

    return (
        <Box sx={{ display: 'flex' }}>
            <CssBaseline />

            {/* 1. Top bar (AppBar) */}
            <AppBar
                position="fixed"
                sx={{
                    width: { sm: `calc(100% - ${drawerWidth}px)` },
                    ml: { sm: `${drawerWidth}px` },
                    bgcolor: 'background.paper',
                    color: 'text.primary',
                    boxShadow: 'none',
                    borderBottom: '1px solid rgba(0, 0, 0, 0.12)'
                }}
            >
                <Toolbar>
                    <IconButton
                        color="inherit"
                        aria-label="open drawer"
                        edge="start"
                        onClick={handleDrawerToggle}
                        sx={{ mr: 2, display: { sm: 'none' } }}
                    >
                        <MenuIcon />
                    </IconButton>

                    <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
                        Dashboard
                    </Typography>
                </Toolbar>
            </AppBar>

            {/* 2. Navigation container (for adaptability) */}
            <Box
                component="nav"
                sx={{ width: { sm: drawerWidth }, flexShrink: { sm: 0 } }}
            >
                {/* Mobile (temporary) panel */}
                <Drawer
                    variant="temporary"
                    open={mobileOpen}
                    onClose={handleDrawerToggle}
                    ModalProps={{ keepMounted: true }}
                    sx={{
                        display: { xs: 'block', sm: 'none' },
                        '& .MuiDrawer-paper': {
                            boxSizing: 'border-box',
                            width: drawerWidth,
                            bgcolor: 'primary.main',
                            color: 'white',
                            borderRight: 'none',
                        },
                    }}
                >
                    {drawerContent}
                </Drawer>

                {/* Desktop (permanent) panel */}
                <Drawer
                    variant="permanent"
                    sx={{
                        display: { xs: 'none', sm: 'block' },
                        '& .MuiDrawer-paper': {
                            boxSizing: 'border-box',
                            width: drawerWidth,
                            bgcolor: 'primary.main',
                            color: 'white',
                            borderRight: 'none',
                        },
                    }}
                    open
                >
                    {drawerContent}
                </Drawer>
            </Box>

            {/* User drop-down menu */}
            <Menu
                id="user-menu"
                anchorEl={anchorEl}
                open={isMenuOpen}
                onClose={handleMenuClose}
                disableRestoreFocus
                anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
                transformOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            >
                <MenuItem onClick={handleMenuClose}>
                    <ListItemIcon><SettingsIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>User Settings</ListItemText>
                </MenuItem>
                <MenuItem onClick={handleLogout}>
                    <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
                    <ListItemText>Logout</ListItemText>
                </MenuItem>
            </Menu>

            {/* 3. Main content */}
            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    p: 3,
                    width: { sm: `calc(100% - ${drawerWidth}px)` },
                    bgcolor: 'background.default'
                }}
            >
                <Toolbar />
                <Outlet />
            </Box>
        </Box>
    );

};

export default Layout;