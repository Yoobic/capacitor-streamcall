import { registerPlugin } from '@capacitor/core';

import type { StreamCallPlugin } from './definitions';

const StreamCall = registerPlugin<StreamCallPlugin>('StreamCall', {
  web: () => import('./web').then((m) => new m.StreamCallWeb()),
});

export * from './definitions';
export { StreamCall };
