import { WebPlugin } from '@capacitor/core';
import type { Call, CallResponse } from "@stream-io/video-client";
import { StreamVideoClient } from "@stream-io/video-client";

import type { CallOptions, StreamCallPlugin, SuccessResponse, LoginOptions} from './definitions';

export class StreamCallWeb extends WebPlugin implements StreamCallPlugin {
  private client?: StreamVideoClient;
  private currentCall?: Call;
  private callStateSubscription?: { unsubscribe: () => void };
  private incomingCall?: CallResponse;

  private setupCallStateListener() {
    this.client?.on('call.ring', (event) => {
      this.incomingCall = event.call;
      this.notifyListeners('callRinging', { callId: event.call.id });
    });
    this.client?.on('call.accepted', (event) => {
      this.notifyListeners('callStarted', { callId: event.call.id });
    });
    this.client?.on('call.ended', (event) => {
      this.notifyListeners('callEnded', { callId: event.call.id });
    });
  }

  async login(options: LoginOptions): Promise<SuccessResponse> {
    this.client = new StreamVideoClient({
      apiKey: options.apiKey,
    });

    await this.client.connectUser(
      { id: options.userId, name: options.name, image: options.imageURL },
      options.token,
    );
    return { success: true };
  }

  async logout(): Promise<SuccessResponse> {
    if (!this.client) {
      throw new Error('Client not initialized');
    }
    
    // Cleanup subscription
    this.callStateSubscription?.unsubscribe();
    this.callStateSubscription = undefined;
    
    await this.client.disconnectUser();
    this.client = undefined;
    this.currentCall = undefined;
    return { success: true };
  }

  async call(options: CallOptions): Promise<SuccessResponse> {
    if (!this.client) {
      throw new Error('Client not initialized - Please login first');
    }
    
    const call = this.client.call(options.type || 'default', crypto.randomUUID());
    this.setupCallStateListener();
    
    await call.getOrCreate({ data: { members: [{ user_id: options.userId }] } });
    if (options.ring) {
      await call.ring();
    }
    
    this.currentCall = call;
    return { success: true };
  }

  async endCall(): Promise<SuccessResponse> {
    if (!this.currentCall) {
      throw new Error('No active call');
    }
    
    await this.currentCall.leave();
    this.currentCall = undefined;
    
    return { success: true };
  }

  async setMicrophoneEnabled(options: { enabled: boolean }): Promise<SuccessResponse> {
    if (!this.currentCall) {
      throw new Error('No active call');
    }
    
    if (options.enabled) {
      await this.currentCall.microphone.enable();
    } else {
      await this.currentCall.microphone.disable();
    }
    
    return { success: true };
  }

  async setCameraEnabled(options: { enabled: boolean }): Promise<SuccessResponse> {
    if (!this.currentCall) {
      throw new Error('No active call');
    }
    
    if (options.enabled) {
      await this.currentCall.camera.enable();
    } else {
      await this.currentCall.camera.disable();
    }
    
    return { success: true };
  }

  async acceptCall(): Promise<SuccessResponse> {
    if (!this.incomingCall || !this.client) {
      throw new Error('No incoming call to accept');
    }
    
    const call: Call = this.client.call(this.incomingCall.type, this.incomingCall.id);
    await call.accept();
    this.currentCall = call;
    this.incomingCall = undefined;
    
    return { success: true };
  }

  async rejectCall(): Promise<SuccessResponse> {
    if (!this.incomingCall || !this.client) {
      throw new Error('No incoming call to reject');
    }
    
    const call: Call = this.client.call(this.incomingCall.type, this.incomingCall.id);
    await call.reject();
    this.incomingCall = undefined;
    
    return { success: true };
  }
}
