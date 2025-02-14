import { WebPlugin } from '@capacitor/core';
import type { Call, CallResponse, StreamVideoParticipant } from "@stream-io/video-client";
import { CallingState, StreamVideoClient } from "@stream-io/video-client";

import type { CallOptions, StreamCallPlugin, SuccessResponse, LoginOptions} from './definitions';

export class StreamCallWeb extends WebPlugin implements StreamCallPlugin {
  private client?: StreamVideoClient;
  private currentCall?: Call;
  private callStateSubscription?: { unsubscribe: () => void };
  private incomingCall?: CallResponse;
  private outgoingCall?: string;
  private magicDivId?: string;
  private videoBindings: Map<string, () => void> = new Map();
  private audioBindings: Map<string, () => void> = new Map();

  private setupCallStateListener() {
    this.client?.on('call.ring', (event) => {
      console.log('Call ringing', event, this.currentCall);
      this.incomingCall = event.call;
      if (!this.currentCall) {
        console.log('Creating new call', event.call.id);
        this.currentCall = this.client?.call(event.call.type, event.call.id);
        this.notifyListeners('callEvent', { callId: event.call.id, state: CallingState.RINGING });
      }
      if (this.currentCall) {
        console.log('Call found', this.currentCall.id);
        this.currentCall?.state.callingState$.subscribe((s) => {
          console.log('Call state', s);
          if (s === CallingState.JOINED) {
            this.setupParticipantListener();
          } else if (s === CallingState.LEFT || s === CallingState.RECONNECTING_FAILED) {
            this.cleanupCall();
          }
          if (this.outgoingCall && s === CallingState.RINGING) {
            this.outgoingCall = undefined;
          } else {
            this.notifyListeners('callEvent', { callId: this.currentCall?.id, state: s });
          }
        })
      }
    });
  }

  private setupParticipantListener() {
    // Subscribe to participant changes
    this.incomingCall = undefined;
    if (!this.currentCall) return;
    this.currentCall?.on('participantJoined', (event) => {
      if (this.magicDivId && event.participant) {
        const magicDiv = document.getElementById(this.magicDivId);
        if (magicDiv && this.currentCall) {
          this.setupParticipantVideo(this.currentCall, event.participant, magicDiv);
          this.setupParticipantAudio(this.currentCall, event.participant, magicDiv);
        }
      }
    });

    // Setup initial participants
    const participants = this.currentCall.state.participants;
    if (this.magicDivId) {
      const magicDiv = document.getElementById(this.magicDivId);
      if (magicDiv) {
        participants.forEach((participant: StreamVideoParticipant) => {
          if (this.currentCall) {
            this.setupParticipantVideo(this.currentCall, participant, magicDiv);
            this.setupParticipantAudio(this.currentCall, participant, magicDiv);
          }
        });
      }
    }
  }

  private setupParticipantVideo(call: Call, participant: StreamVideoParticipant, container: HTMLElement) {
    const id = `video-${participant.sessionId}`;
    if (!document.getElementById(id)) {
      const videoEl = document.createElement('video');
      videoEl.id = id;
      videoEl.style.width = '100%';
      videoEl.style.maxWidth = '300px';
      videoEl.style.aspectRatio = '16/9';
      container.appendChild(videoEl);

      const unbind = call.bindVideoElement(videoEl, participant.sessionId, 'videoTrack');
      if (unbind) this.videoBindings.set(id, unbind);
    }
  }

  private setupParticipantAudio(call: Call, participant: StreamVideoParticipant, container: HTMLElement) {
    if (participant.isLocalParticipant) return;

    const id = `audio-${participant.sessionId}`;
    if (!document.getElementById(id)) {
      const audioEl = document.createElement('audio');
      audioEl.id = id;
      container.appendChild(audioEl);

      const unbind = call.bindAudioElement(audioEl, participant.sessionId);
      if (unbind) this.audioBindings.set(id, unbind);
    }
  }

  private cleanupCall() {
    if (this.magicDivId) {
      const magicDiv = document.getElementById(this.magicDivId);
      if (magicDiv) {
        magicDiv.innerHTML = '';
      }
    }
    this.videoBindings.forEach((unbind) => unbind());
    this.videoBindings.clear();
    this.audioBindings.forEach((unbind) => unbind());
    this.audioBindings.clear();
    this.currentCall = undefined;
    this.incomingCall = undefined;
  }

  async login(options: LoginOptions): Promise<SuccessResponse> {
    this.client = StreamVideoClient.getOrCreateInstance({
      apiKey: options.apiKey,
      user: { id: options.userId, name: options.name, image: options.imageURL },
      token: options.token,
    });

    this.magicDivId = options.magicDivId;
    this.setupCallStateListener();

    return { success: true };
  }

  async logout(): Promise<SuccessResponse> {
    if (!this.client) {
      console.log('No client', this.client);
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
      console.log('No client', this.client);
      throw new Error('Client not initialized - Please login first');
    }
    
    const call = this.client.call(options.type || 'default', crypto.randomUUID());
    await call.getOrCreate({ data: { members: [{ user_id: options.userId }] } });
    this.currentCall = call;
    if (options.ring) {
      this.outgoingCall =  call.cid;
      await call.ring();
    }
    
    await call.join();
    return { success: true };
  }

  async endCall(): Promise<SuccessResponse> {
    if (!this.currentCall) {
      console.log('No active call', this.currentCall);
      throw new Error('No active call');
    }
    
    await this.currentCall.leave();
    this.currentCall = undefined;
    
    return { success: true };
  }

  async setMicrophoneEnabled(options: { enabled: boolean }): Promise<SuccessResponse> {
    if (!this.currentCall) {
      console.log('No active call', this.currentCall);
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
      console.log('No active call', this.currentCall);
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
      console.log('No incoming call to accept', this.incomingCall, this.client);
      throw new Error('No incoming call to accept');
    }
    console.log('Accepting call', this.incomingCall);
    const call = this.client.call(this.incomingCall.type, this.incomingCall.id);
    this.currentCall = call;
    console.log('Joining call', call);
    await call.join();
    console.log('Joined call', call);
    this.notifyListeners('callEvent', { callId: call.id, state: CallingState.JOINED });
    this.setupParticipantListener();
    return { success: true }
  }

  async rejectCall(): Promise<SuccessResponse> {
    if (!this.incomingCall || !this.client) {
      console.log('No incoming call to reject', this.incomingCall, this.client);
      throw new Error('No incoming call to reject');
    }
    console.log('Rejecting call', this.incomingCall);
    const call: Call = this.client.call(this.incomingCall.type, this.incomingCall.id);
    console.log('Leaving call', call);
    await call.leave();
    this.incomingCall = undefined;
    console.log('Rejected call', call);
    this.notifyListeners('callEvent', { callId: call.id, state: CallingState.LEFT });
    this.cleanupCall();
    return { success: true };
  }
}
