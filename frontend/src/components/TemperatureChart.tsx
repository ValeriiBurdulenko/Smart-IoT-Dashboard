import React, { useMemo } from 'react';
import {
    AreaChart,
    Area,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip as RechartsTooltip,
    ResponsiveContainer
} from 'recharts';
import { Box, Typography, CircularProgress, useTheme } from '@mui/material';
import { format } from 'date-fns';
import AnalyticsIcon from '@mui/icons-material/BarChart';
import SignalWifiOffIcon from '@mui/icons-material/SignalWifiOff';

import type { HistoryPoint } from '../services/ApiService';

interface TemperatureChartProps {
    data: HistoryPoint[];
    height?: number;
    loading?: boolean;
    isOffline?: boolean;
    emptyMessage?: string;
    offlineMessage?: string;
}

/**
 * Formats timestamp for X-axis display
 */
const formatXAxisTime = (timestamp: string): string => {
    try {
        return format(new Date(timestamp), 'HH:mm');
    } catch (e) {
        console.warn('Error formatting time:', e);
        return '?';
    }
};

/**
 * Formats timestamp for tooltip display
 */
const formatTooltipTime = (timestamp: string): string => {
    try {
        return format(new Date(timestamp), 'dd.MM.yyyy HH:mm:ss');
    } catch (e) {
        console.warn('Error formatting tooltip time:', e);
        return '?';
    }
};

/**
 * Formats temperature value
 */
const formatTemperature = (value: number): string => {
    if (typeof value !== 'number' || isNaN(value)) {
        return 'N/A';
    }
    return `${value.toFixed(1)}°C`;
};

const TemperatureChart: React.FC<TemperatureChartProps> = ({
    data,
    height = 350,
    loading = false,
    isOffline = false,
    emptyMessage = 'Warte auf Verbindung...',
    offlineMessage = 'Keine Daten vom Gerät'
}) => {
    const theme = useTheme();

    // ━━━ Memoized calculations ━━━
    const hasData = useMemo(() => data && data.length > 0, [data]);

    return (
        <Box
            sx={{
                width: '100%',
                height,
                minHeight: height,
                bgcolor: '#fbfbfb',
                borderRadius: 2,
                border: `1px dashed ${theme.palette.divider}`,
                position: 'relative',
                overflow: 'hidden'
            }}
        >
            {/* Offline Overlay */}
            {isOffline && (
                <Box
                    sx={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        bgcolor: 'rgba(255, 255, 255, 0.7)',
                        backdropFilter: 'blur(2px)',
                        zIndex: 10,
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'text.secondary'
                    }}
                >
                    <SignalWifiOffIcon
                        sx={{
                            fontSize: 48,
                            mb: 1,
                            color: 'text.disabled'
                        }}
                    />
                    <Typography variant="h6" fontWeight="bold">
                        {offlineMessage}
                    </Typography>
                    {hasData && (
                        <Typography variant="caption">
                            Veraltete Daten werden angezeigt
                        </Typography>
                    )}
                </Box>
            )}

            {/* Chart */}
            {hasData ? (
                <ResponsiveContainer width="100%" height="100%">
                    <AreaChart
                        data={data}
                        margin={{ top: 10, right: 10, left: 0, bottom: 0 }}
                    >
                        {/* Gradient Definition */}
                        <defs>
                            <linearGradient id="colorTemp" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#1976d2" stopOpacity={0.3} />
                                <stop offset="95%" stopColor="#1976d2" stopOpacity={0} />
                            </linearGradient>
                        </defs>

                        {/* Grid */}
                        <CartesianGrid
                            strokeDasharray="3 3"
                            vertical={false}
                            stroke="#eee"
                        />

                        {/* X-Axis */}
                        <XAxis
                            dataKey="timestamp"
                            tickFormatter={formatXAxisTime}
                            stroke="#9e9e9e"
                            fontSize={12}
                            minTickGap={50}
                            tickLine={false}
                            axisLine={false}
                        />

                        {/* Y-Axis */}
                        <YAxis
                            domain={['auto', 'auto']}
                            stroke="#9e9e9e"
                            fontSize={12}
                            unit="°"
                            tickLine={false}
                            axisLine={false}
                            width={50}
                        />

                        {/* Tooltip */}
                        <RechartsTooltip
                            labelFormatter={formatTooltipTime}
                            formatter={(value: any) => [
                                formatTemperature(value),
                                'Temperatur'
                            ]}
                            contentStyle={{
                                borderRadius: 12,
                                border: 'none',
                                boxShadow: '0 8px 16px rgba(0,0,0,0.1)'
                            }}
                        />

                        {/* Area */}
                        <Area
                            type="monotone"
                            dataKey="temperature"
                            stroke="#1976d2"
                            strokeWidth={3}
                            fillOpacity={1}
                            fill="url(#colorTemp)"
                            isAnimationActive={false}
                            connectNulls={true}
                        />
                    </AreaChart>
                </ResponsiveContainer>
            ) : !isOffline ? (
                /* Empty State */
                <Box
                    sx={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexDirection: 'column',
                        bgcolor: '#fafafa'
                    }}
                >
                    {loading ? (
                        <>
                            <CircularProgress
                                size={30}
                                sx={{ mb: 2, color: 'text.disabled' }}
                            />
                            <Typography color="text.disabled">
                                {emptyMessage}
                            </Typography>
                        </>
                    ) : (
                        <>
                            <AnalyticsIcon
                                sx={{ fontSize: 40, color: '#ddd', mb: 1 }}
                            />
                            <Typography color="text.disabled">
                                {emptyMessage}
                            </Typography>
                        </>
                    )}
                </Box>
            ) : null}
        </Box>
    );
};

export default TemperatureChart;