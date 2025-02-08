import { WebPlugin } from '@capacitor/core';

import type { CallOptions, StreamCallPlugin, SuccessResponse } from './definitions';

export class StreamCallWeb extends WebPlugin implements StreamCallPlugin {
  endCall(): Promise<SuccessResponse> {
    throw new Error('Method not implemented.');
  }
  setMicrophoneEnabled(_options: { enabled: boolean; }): Promise<SuccessResponse> {
    throw new Error('Method not implemented.');
  }
  setCameraEnabled(_options: { enabled: boolean; }): Promise<SuccessResponse> {
    throw new Error('Method not implemented.');
  }
  call(_options: CallOptions): Promise<SuccessResponse> {
    return Promise.reject('Unimplemented');
  }
  echo(_options: { value: string; }): Promise<{ value: string; }> {
    return Promise.reject('Unimplemented');
  }

  initialize(): Promise<void> {
    return Promise.reject('Unimplemented');
  }

  login(_options: any): Promise<any> {
    // Implement login logic
    return Promise.reject('Unimplemented');
  }

  logout(): Promise<any> {
    // Implement logout logic
    return Promise.reject('Unimplemented');
  }
}