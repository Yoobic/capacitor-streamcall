import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'app.capgo.streamcall',
  appName: 'Stream Call Example',
  webDir: 'www',
  "plugins": {
    "Keyboard": {
      "resize": "body",
      "style": "DARK",
      "resizeOnFullScreen": false
    }
  }
};

export default config;
