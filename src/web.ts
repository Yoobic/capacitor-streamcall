import { WebPlugin } from '@capacitor/core';
import type { AllClientEvents, Call, CallResponse, StreamVideoParticipant } from '@stream-io/video-client';
import { CallingState, StreamVideoClient } from '@stream-io/video-client';

import type {
  CallOptions,
  StreamCallPlugin,
  SuccessResponse,
  LoginOptions,
  CameraEnabledResponse,
} from './definitions';

export class StreamCallWeb extends WebPlugin implements StreamCallPlugin {
  private client?: StreamVideoClient;
  private currentCall?: Call;
  private callStateSubscription?: { unsubscribe: () => void };
  private incomingCall?: CallResponse;
  private outgoingCall?: string;
  private magicDivId?: string;
  private videoBindings: Map<string, () => void> = new Map();
  private audioBindings: Map<string, () => void> = new Map();
  private participantJoinedListener?: (event: { participant?: { sessionId: string } }) => void;
  private participantLeftListener?: (event: { participant?: { sessionId: string } }) => void;
  private participantResponses: Map<string, string> = new Map();

  private setupCallRingListener() {
    this.client?.off('call.ring', this.ringCallback);
    this.client?.on('call.ring', this.ringCallback);
  }

  private setupCallEventListeners() {
    // Clear previous listeners if any
    this.client?.off('call.rejected', this.callRejectedCallback);
    this.client?.off('call.accepted', this.callAcceptedCallback);
    this.client?.off('call.missed', this.callMissedCallback);
    
    // Register event listeners
    this.client?.on('call.rejected', this.callRejectedCallback);
    this.client?.on('call.accepted', this.callAcceptedCallback);
    this.client?.on('call.missed', this.callMissedCallback);
  }

  private ringCallback = (event: AllClientEvents['call.ring']) => {
    console.log('Call ringing', event, this.currentCall);
    this.incomingCall = event.call;
    if (!this.currentCall) {
      console.log('Creating new call', event.call.id);
      this.currentCall = this.client?.call(event.call.type, event.call.id);
      this.notifyListeners('callEvent', { callId: event.call.id, state: CallingState.RINGING });
      // Clear previous responses when a new call starts
      this.participantResponses.clear();
    }
    if (this.currentCall) {
      console.log('Call found', this.currentCall.id);
      this.callStateSubscription = this.currentCall?.state.callingState$.subscribe((s) => {
        console.log('Call state', s);
        if (s === CallingState.JOINED) {
          this.setupParticipantListener();
          this.setupCallEventListeners();
        } else if (s === CallingState.LEFT || s === CallingState.RECONNECTING_FAILED) {
          this.cleanupCall();
        }
        if (this.outgoingCall && s === CallingState.RINGING) {
          this.outgoingCall = undefined;
        } else {
          this.notifyListeners('callEvent', { callId: this.currentCall?.id, state: s });
        }
      });
    }
  };

  private setupParticipantListener() {
    // Subscribe to participant changes
    this.incomingCall = undefined;
    if (!this.currentCall) return;

    this.participantJoinedListener = (event) => {
      if (this.magicDivId && event.participant) {
        const magicDiv = document.getElementById(this.magicDivId);
        if (magicDiv && this.currentCall) {
          this.setupParticipantVideo(this.currentCall, event.participant as StreamVideoParticipant, magicDiv);
          this.setupParticipantAudio(this.currentCall, event.participant as StreamVideoParticipant, magicDiv);
        }
      }
    };

    this.participantLeftListener = (event) => {
      if (this.magicDivId && event.participant) {
        const videoId = `video-${event.participant.sessionId}`;
        const audioId = `audio-${event.participant.sessionId}`;

        // Remove video element
        const videoEl = document.getElementById(videoId) as HTMLVideoElement;
        if (videoEl) {
          const unbindVideo = this.videoBindings.get(videoId);
          if (unbindVideo) {
            unbindVideo();
            this.videoBindings.delete(videoId);
          }
          const tracks = videoEl.srcObject as MediaStream;
          if (tracks) {
            tracks.getTracks().forEach((track) => {
              track.stop();
              track.enabled = false;
            });
            videoEl.srcObject = null;
          }
          videoEl.remove();
        }

        // Remove audio element
        const audioEl = document.getElementById(audioId) as HTMLAudioElement;
        if (audioEl) {
          const unbindAudio = this.audioBindings.get(audioId);
          if (unbindAudio) {
            unbindAudio();
            this.audioBindings.delete(audioId);
          }
          const tracks = audioEl.srcObject as MediaStream;
          if (tracks) {
            tracks.getTracks().forEach((track) => {
              track.stop();
              track.enabled = false;
            });
            audioEl.srcObject = null;
          }
          audioEl.remove();
        }
      }
    };

    this.currentCall.on('participantJoined', this.participantJoinedListener);
    this.currentCall.on('participantLeft', this.participantLeftListener);

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

  private callRejectedCallback = (event: AllClientEvents['call.rejected']) => {
    console.log('Call rejected', event);
    if (event.user && event.user.id) {
      this.participantResponses.set(event.user.id, 'rejected');
      this.notifyListeners('callEvent', { 
        callId: event.call_cid, 
        state: 'rejected',
        userId: event.user.id
      });
      
      this.checkAllParticipantsResponded();
    }
  };

  private callAcceptedCallback = (event: AllClientEvents['call.accepted']) => {
    console.log('Call accepted', event);
    if (event.user && event.user.id) {
      this.participantResponses.set(event.user.id, 'accepted');
      this.notifyListeners('callEvent', { 
        callId: event.call_cid, 
        state: 'accepted',
        userId: event.user.id
      });
    }
  };

  private callMissedCallback = (event: AllClientEvents['call.missed']) => {
    console.log('Call missed', event);
    if (event.user && event.user.id) {
      this.participantResponses.set(event.user.id, 'missed');
      this.notifyListeners('callEvent', { 
        callId: event.call_cid, 
        state: 'missed',
        userId: event.user.id
      });
      
      this.checkAllParticipantsResponded();
    }
  };

  private checkAllParticipantsResponded() {
    if (!this.currentCall) return;
    
    // Get total participants from the call state
    const participants = this.currentCall.state.participantCount || 0;
    // We subtract 1 to exclude the current user
    const expectedResponses = Math.max(0, participants - 1);
    
    // Count rejections and misses
    let rejectedOrMissedCount = 0;
    this.participantResponses.forEach(response => {
      if (response === 'rejected' || response === 'missed') {
        rejectedOrMissedCount++;
      }
    });
    
    console.log(`Participants responded: ${this.participantResponses.size}/${expectedResponses}`);
    console.log(`Rejected or missed: ${rejectedOrMissedCount}`);
    
    // If all participants have rejected or missed the call
    if (rejectedOrMissedCount > 0 && rejectedOrMissedCount >= expectedResponses) {
      console.log('All participants have rejected or missed the call');
      
      // End the call
      this.currentCall.leave();
      
      // Clean up
      this.cleanupCall();
      
      // Notify listeners
      this.notifyListeners('callEvent', { 
        callId: this.currentCall.id, 
        state: 'ended',
        reason: 'all_rejected_or_missed'
      });
    }
  }

  private cleanupCall() {
    // First cleanup the call listeners
    if (this.currentCall) {
      if (this.participantJoinedListener) {
        this.currentCall.off('participantJoined', this.participantJoinedListener);
        this.participantJoinedListener = undefined;
      }
      if (this.participantLeftListener) {
        this.currentCall.off('participantLeft', this.participantLeftListener);
        this.participantLeftListener = undefined;
      }
      this.callStateSubscription?.unsubscribe();
    }

    if (this.magicDivId) {
      const magicDiv = document.getElementById(this.magicDivId);
      if (magicDiv) {
        // Remove all video elements
        const videoElements = magicDiv.querySelectorAll('video');
        videoElements.forEach((video) => {
          const id = video.id;
          const unbind = this.videoBindings.get(id);
          if (unbind) {
            unbind();
            this.videoBindings.delete(id);
          }
          // Stop all tracks
          const tracks = (video as HTMLVideoElement).srcObject as MediaStream;
          if (tracks) {
            tracks.getTracks().forEach((track) => {
              track.stop();
              track.enabled = false;
            });
            video.srcObject = null;
          }
          video.remove();
        });

        // Remove all audio elements
        const audioElements = magicDiv.querySelectorAll('audio');
        audioElements.forEach((audio) => {
          const id = audio.id;
          const unbind = this.audioBindings.get(id);
          if (unbind) {
            unbind();
            this.audioBindings.delete(id);
          }
          // Stop all tracks
          const tracks = (audio as HTMLAudioElement).srcObject as MediaStream;
          if (tracks) {
            tracks.getTracks().forEach((track) => {
              track.stop();
              track.enabled = false;
            });
            audio.srcObject = null;
          }
          audio.remove();
        });

        // Clear the container
        while (magicDiv.firstChild) {
          magicDiv.removeChild(magicDiv.firstChild);
        }
      }
    }

    // Clear all bindings
    this.videoBindings.clear();
    this.audioBindings.clear();

    // Clear call references
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
    this.setupCallRingListener();

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
    const members = options.userIds.map((userId) => ({ user_id: userId }));
    if (this.client.streamClient.userID && !options.userIds.includes(this.client.streamClient.userID)) {
      members.push({ user_id: this.client.streamClient.userID });
    }
    await call.getOrCreate({ data: { members } });
    this.currentCall = call;
    if (options.ring) {
      this.outgoingCall = call.cid;
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
    this.cleanupCall();

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
    await call.accept();
    await call.join();
    console.log('Joined call', call);
    this.notifyListeners('callEvent', { callId: call.id, state: CallingState.JOINED });
    this.setupParticipantListener();
    return { success: true };
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

  async isCameraEnabled(): Promise<CameraEnabledResponse> {
    if (!this.currentCall) {
      console.log('No active call', this.currentCall);
      throw new Error('No active call');
    }
    const enabled = await this.currentCall.camera.enabled;
    return { enabled };
  }
}
