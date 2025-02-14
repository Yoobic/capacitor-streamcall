export interface LoginOptions {
  token: string;
  userId: string;
  name: string;
  imageURL?: string;
  apiKey: string;
  magicDivId?: string;
  refreshToken?: {
    url: string;
    headers?: Record<string, string>;
  };
}

export interface CallOptions {
  userId: string;
  type?: string;  // default if not specified
  ring?: boolean; // true if not specified
}

export interface SuccessResponse {
  success: boolean;
}

export interface CallEvent {
  callId: string;
  state: string;
}

export interface StreamCallPlugin {
  login(options: LoginOptions): Promise<SuccessResponse>;
  logout(): Promise<SuccessResponse>;
  call(options: CallOptions): Promise<SuccessResponse>;
  endCall(): Promise<SuccessResponse>;
  setMicrophoneEnabled(options: { enabled: boolean }): Promise<SuccessResponse>;
  setCameraEnabled(options: { enabled: boolean }): Promise<SuccessResponse>;
  
  addListener(
    eventName: 'callEvent',
    listenerFunc: (event: CallEvent) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  
  removeAllListeners(): Promise<void>;

  acceptCall(): Promise<SuccessResponse>;
  rejectCall(): Promise<SuccessResponse>;
}
