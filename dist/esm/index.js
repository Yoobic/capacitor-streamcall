import { registerPlugin } from '@capacitor/core';
const StreamCall = registerPlugin('StreamCall', {
    web: () => import('./web').then((m) => new m.StreamCallWeb()),
});
export * from './definitions';
export { StreamCall };
//# sourceMappingURL=index.js.map