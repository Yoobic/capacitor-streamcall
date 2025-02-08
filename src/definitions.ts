export interface LoginOptions {
  token: string;
  userId: string;
  name: string;
  imageURL?: string;
}

export interface CallOptions {
  userId: string;
  type?: string;  // default if not specified
  ring?: boolean; // true if not specified
}

export interface SuccessResponse {
  success: boolean;
}

export interface CallStartedEvent {
  callId: string;
}

export interface StreamCallPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  initialize(): Promise<void>;
  login(options: LoginOptions): Promise<SuccessResponse>;
  logout(): Promise<SuccessResponse>;
  call(options: CallOptions): Promise<SuccessResponse>;
  endCall(): Promise<SuccessResponse>;
  setMicrophoneEnabled(options: { enabled: boolean }): Promise<SuccessResponse>;
  setCameraEnabled(options: { enabled: boolean }): Promise<SuccessResponse>;
  
  addListener(
    eventName: 'callStarted',
    listenerFunc: (event: CallStartedEvent) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  
  addListener(
    eventName: 'callEnded',
    listenerFunc: (event: {}) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  
  removeAllListeners(): Promise<void>;
}
