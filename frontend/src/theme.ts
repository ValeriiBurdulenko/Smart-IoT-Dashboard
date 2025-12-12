// src/theme.ts
import { createTheme } from '@mui/material/styles';

// Deine Farbvorgaben
const PRIMARY_COLOR = 'rgb(77, 107, 221)';  // #4d6bdd
const TEXT_COLOR = 'rgb(21, 40, 75)';       // #15284b
const BG_COLOR = 'rgb(255, 255, 255)';      // #ffffff

// Im Screenshot ist der *Hintergrund* (default) leicht grau, 
// während die *Karten* (paper) weiß sind.
const BG_DEFAULT_COLOR = '#f4f7f6'; // Sehr heller Grauton

export const lightTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: PRIMARY_COLOR, // Dein Blau für Buttons und Akzente
    },
    background: {
      default: BG_DEFAULT_COLOR, // Hellgrauer "Körper"-Hintergrund
      paper: BG_COLOR,         // Weißer Hintergrund für Karten/AppBar/Sidebar
    },
    text: {
      primary: TEXT_COLOR,       // Dein Haupt-Text-Dunkelblau
      secondary: '#5a6978',      // Ein weicheres Grau für Nebentext
    },
  },
  shape: {
    borderRadius: 8, // Leicht abgerundete Ecken
  },
  typography: {
    fontFamily: ['Roboto', '-apple-system', 'sans-serif'].join(','),
    h6: {
      fontWeight: 600, // Etwas fettere Überschriften für Karten
    },
  },
});