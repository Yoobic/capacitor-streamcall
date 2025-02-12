import { Component } from '@angular/core';
import { ChangeDetectorRef } from '@angular/core';
import { StreamCall } from 'stream-call';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
  standalone: false,
})
export class AppComponent {
  constructor(private cdr: ChangeDetectorRef) {}

  apiKey = 'n8wv8vjmucdw';
  isInCall = false;
  isMuted = false;
  isCameraOff = false;

  async endCall() {
    await StreamCall.endCall();
    this.isInCall = false;
    this.cdr.detectChanges();
  }

  async clickEvent() {
    console.log('Making app transparent and initializing StreamCall');
    
    // Add transparent background style
    const styleElement = document.createElement('style');
    styleElement.id = 'magic_transparent_background';
    styleElement.textContent = `
      :root {
        --ion-background-color: transparent !important;
      }
      ion-content {
        --background: transparent !important;
      }
      .ion-page {
        background: transparent !important;
      }
    `;
    document.head.appendChild(styleElement);

    // register event listeners
    StreamCall.addListener('callStarted', (event) => {
      this.isInCall = true;
      this.cdr.detectChanges();
    });
    StreamCall.addListener('callEnded', (event) => {
      this.isInCall = false;
      this.cdr.detectChanges();
    });
  }

  async toggleMute() {
    this.isMuted = !this.isMuted;
    await StreamCall.setMicrophoneEnabled({ enabled: !this.isMuted });
    this.cdr.detectChanges();
  }

  async toggleCamera() {
    this.isCameraOff = !this.isCameraOff;
    await StreamCall.setCameraEnabled({ enabled: !this.isCameraOff });
    this.cdr.detectChanges();
  }

  ngOnInit() {
    this.clickEvent();
  }
}
