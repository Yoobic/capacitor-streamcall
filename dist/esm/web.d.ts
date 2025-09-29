import { WebPlugin } from '@capacitor/core';
import type { CallOptions, StreamCallPlugin, SuccessResponse, LoginOptions, CameraEnabledResponse, CallEvent, DynamicApiKeyResponse, CurrentUserResponse } from './definitions';
export declare class StreamCallWeb extends WebPlugin implements StreamCallPlugin {
    private client?;
    private currentCall?;
    private callStateSubscription?;
    private incomingCall?;
    private outgoingCall?;
    private magicDivId?;
    private videoBindings;
    private audioBindings;
    private participantJoinedListener?;
    private participantLeftListener?;
    private participantResponses;
    private callMembersExpected;
    private currentUser?;
    private setupCallRingListener;
    private setupCallEventListeners;
    private ringCallback;
    private setupParticipantListener;
    private setupParticipantVideo;
    private setupParticipantAudio;
    private callSessionStartedCallback;
    private callRejectedCallback;
    private callAcceptedCallback;
    private callMissedCallback;
    private callStates;
    private checkCallTimeout;
    private checkAllParticipantsResponded;
    private cleanupCall;
    login(options: LoginOptions): Promise<SuccessResponse>;
    logout(): Promise<SuccessResponse>;
    call(options: CallOptions): Promise<SuccessResponse>;
    endCall(): Promise<SuccessResponse>;
    setMicrophoneEnabled(options: {
        enabled: boolean;
    }): Promise<SuccessResponse>;
    setCameraEnabled(options: {
        enabled: boolean;
    }): Promise<SuccessResponse>;
    setSpeaker(options: {
        name: string;
    }): Promise<SuccessResponse>;
    switchCamera(options: {
        camera: 'front' | 'back';
    }): Promise<SuccessResponse>;
    acceptCall(): Promise<SuccessResponse>;
    rejectCall(): Promise<SuccessResponse>;
    isCameraEnabled(): Promise<CameraEnabledResponse>;
    getCallInfo(options: {
        callId: string;
    }): Promise<CallEvent>;
    getCallStatus(): Promise<CallEvent>;
    setDynamicStreamVideoApikey(_options: {
        apiKey: string;
    }): Promise<SuccessResponse>;
    getDynamicStreamVideoApikey(): Promise<DynamicApiKeyResponse>;
    getCurrentUser(): Promise<CurrentUserResponse>;
}
