import { WebPlugin } from '@capacitor/core';
import type { Call } from "@stream-io/video-client";
import { StreamVideoClient } from "@stream-io/video-client";

import type { CallOptions, StreamCallPlugin, SuccessResponse, LoginOptions } from './definitions';

export class StreamCallWeb extends WebPlugin implements StreamCallPlugin {
  private client?: StreamVideoClient;
  private currentCall?: Call;
  private callStateSubscription?: { unsubscribe: () => void };

  private setupCallStateListener() {
    // Cleanup previous subscription if any
    // this.callStateSubscription?.unsubscribe();
    
    this.client?.on('call.ring', (event) => {
      this.notifyListeners('callRinging', { callId: event.call.id });
    });
    // this.callStateSubscription = call.state.callingState$.subscribe((state) => {
    //   switch (state) {
    //     case CallingState.JOINED:
    //       this.notifyListeners('callStarted', { callId: call.id });
    //       break;
    //     case CallingState.RINGING:
    //       this.notifyListeners('callRinging', { callId: call.id });
    //       break;
    //     case CallingState.LEFT:
    //     case CallingState.IDLE:
    //       this.notifyListeners('callEnded', { callId: call.id });
    //       this.currentCall = undefined;
    //       break;
    //     case CallingState.RECONNECTING_FAILED:
    //     case CallingState.OFFLINE:
    //       this.notifyListeners('callEnded', { callId: call.id });
    //       this.currentCall = undefined;
    //       break;
    //     case CallingState.JOINING:
    //     case CallingState.RECONNECTING:
    //     case CallingState.MIGRATING:
    //       // These are intermediate states, we don't need to notify
    //       break;
    //     case CallingState.UNKNOWN:
    //       console.warn('Unknown call state');
    //       break;
    //     default:
    //       console.warn('Not handling call state: ', state);
    //   }
    // });
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
}
