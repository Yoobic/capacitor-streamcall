import { WebPlugin } from '@capacitor/core';

import type { StreamCallPlugin } from './definitions';

export class StreamCallWeb extends WebPlugin implements StreamCallPlugin {
  async initialize(): Promise<void> {
    // Implementation of required initialize method
  }

  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
