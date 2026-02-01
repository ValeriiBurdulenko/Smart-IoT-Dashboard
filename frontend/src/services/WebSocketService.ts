import { Client } from '@stomp/stompjs';
import type { IMessage, StompSubscription, IFrame } from '@stomp/stompjs';
import KeycloakService from './KeycloakService';

export interface TelemetryData {
    deviceId: string;
    timestamp: string;
    data: {
        currentTemperature: number;
        targetTemperature?: number;
        heatingStatus: boolean;
    };
}

export interface AlertData {
    alertId: string;
    deviceId: string;
    type: string;
    message: string;
    value: number;
    timestamp: number;
}

export type ConnectionStatus = 'connected' | 'connecting' | 'disconnected' | 'error';

class WebSocketService {
    private client: Client;
    private static instance: WebSocketService;
    
    private status: ConnectionStatus = 'disconnected';
    
    private statusListeners: Set<(status: ConnectionStatus) => void> = new Set();

    private connectQueue: (() => void)[] = [];

    private constructor() {
        this.client = new Client({
            brokerURL: import.meta.env.VITE_WEBSOCKET_URL || 'ws://localhost:8088/ws',
            
            reconnectDelay: 5000,
            
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,

            beforeConnect: () => {
                const token = KeycloakService.getToken();
                if (token) {
                    this.client.connectHeaders = {
                        Authorization: `Bearer ${token}`
                    };
                    console.debug('🔑 WS: Auth token attached');
                } else {
                    console.warn('⚠️ WS: Connecting without token (may fail if auth required)');
                }
            }
        });

        // --- Bearbeitung Event of STOMP ---

        this.client.onConnect = () => {
            console.log('✅ WS: Connected');
            this.updateStatus('connected');

            if (this.connectQueue.length > 0) {
                console.log(`🔄 WS: Processing ${this.connectQueue.length} queued subscriptions`);
                const queue = [...this.connectQueue];
                this.connectQueue = []; 
                queue.forEach(task => task());
            }
        };

        this.client.onDisconnect = () => {
            console.log('🔌 WS: Disconnected');
            this.updateStatus('disconnected');
        };

        this.client.onStompError = (frame: IFrame) => {
            const errorMsg = frame.headers['message'] || frame.body;
    
            if (errorMsg.includes('Access Denied') || errorMsg.includes('do not own')) {
                console.error('🚫 WS: Access denied to device');
                this.updateStatus('error');
            } else {
                console.error('❌ WS: Broker error:', errorMsg);
                this.updateStatus('error');
            }
        };

        this.client.onWebSocketClose = (event) => {
            if (event.code !== 1000) {
                console.warn('⚠️ WS: Socket closed:', event.reason);
                this.updateStatus('disconnected');
            }
        };

        this.client.onWebSocketError = (event) => {
            console.error('❌ WS: Transport error:', event);
            this.updateStatus('error');
        };
    }

    /**
     * Singleton Accessor
     */
    public static getInstance(): WebSocketService {
        if (!WebSocketService.instance) {
            WebSocketService.instance = new WebSocketService();
        }
        return WebSocketService.instance;
    }

    /**
     * Activate conection
     * Call once in the root component (App.tsx)
     */
    public activate(): void {
        if (!this.client.active) {
            this.updateStatus('connecting');
            this.client.activate();
        }
    }

    /**
     * Deactivate connection
     */
    public deactivate(): void {
        if (this.client.active) {
            this.client.deactivate();
            this.updateStatus('disconnected');
        }
    }

    /**
     * Subscription to telemetry for a specific device
     * Automatically activates the client if it is disabled
     */
    public subscribeToDevice(deviceId: string, callback: (data: TelemetryData) => void) {
        return this.genericSubscribe(`/topic/device.${deviceId}`, callback);
    }

    public subscribeToUserAlerts(userId: string, callback: (alert: AlertData) => void) {
        return this.genericSubscribe(`/topic/user.${userId}.alerts`, callback);
    }

    private genericSubscribe<T>(topic: string, callback: (data: T) => void): { unsubscribe: () => void } {
        if (!this.client.active) this.activate();

        let subscription: StompSubscription | null = null;
        
        const doSubscribe = () => {
            console.log(`👂 WS: Subscribing to ${topic}`);
            try {
                subscription = this.client.subscribe(topic, (message: IMessage) => {
                    if (message.body) {
                        try {
                            const data: T = JSON.parse(message.body);
                            callback(data);
                        } catch (e) {
                            console.error('❌ WS: JSON Parse error', e);
                        }
                    }
                });
            } catch (error) {
                console.error("❌ WS: Subscribe failed", error);
            }
        };

        if (this.client.connected) {
            doSubscribe();
        } else {
            console.log(`⏳ WS: Queuing subscription for ${topic}`);
            this.connectQueue.push(doSubscribe);
        }

        return {
            unsubscribe: () => {
                if (subscription) {
                    subscription.unsubscribe();
                    subscription = null;
                } else {
                    const index = this.connectQueue.indexOf(doSubscribe);
                    if (index > -1) {
                        console.log(`🗑️ WS: Removing queued subscription for ${topic}`);
                        this.connectQueue.splice(index, 1);
                    }
                }
            }
        };
    }

    public getStatus(): ConnectionStatus {
        return this.status;
    }

    public isConnected(): boolean {
        return this.status === 'connected';
    }

    /**
     * Subscription to connection status change (for icons in the UI)
     * Returns the unsubscribe function
     */
    public onStatusChange(callback: (status: ConnectionStatus) => void): () => void {
        this.statusListeners.add(callback);
        callback(this.status); 
        
        return () => {
            this.statusListeners.delete(callback);
        };
    }

    private updateStatus(newStatus: ConnectionStatus): void {
        if (this.status !== newStatus) {
            this.status = newStatus;
            this.statusListeners.forEach(listener => listener(newStatus));
        }
    }
}

const webSocketService = WebSocketService.getInstance();
export default webSocketService;