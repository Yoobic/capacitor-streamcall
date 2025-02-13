import { Component } from '@angular/core';
import { StreamCall } from 'stream-call';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ToastController } from '@ionic/angular';

@Component({
  selector: 'app-tab1',
  templateUrl: 'tab1.page.html',
  styleUrls: ['tab1.page.scss'],
  standalone: false,
})
export class Tab1Page {
  private readonly STYLE_ID = 'magic_transparent_background';
  private readonly API_URL = 'https://streamcall-02.localcan.dev';
  private readonly API_KEY = 'n8wv8vjmucdw';
  transparent = false;
  currentUser: {
    userId: string;
    name: string;
    imageURL: string;
  } | null = null;
  incomingCallId: string | null = null;

  constructor(
    private http: HttpClient,
    private toastController: ToastController
  ) {
    void this.loadStoredUser();
    StreamCall.removeAllListeners();
    // listen to the call event
    StreamCall.addListener('callRinging', async (data) => {
      console.log('Call ringing', data);
      this.incomingCallId = data.callId;
      await this.presentIncomingCallToast();
    });
    StreamCall.addListener('callStarted', (data) => {
      console.log('Call started', data);
      this.incomingCallId = null;
    });
    StreamCall.addListener('callEnded', (data) => {
      console.log('Call ended', data);
      this.incomingCallId = null;
    });
  }

  private async loadStoredUser() {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      this.currentUser = JSON.parse(storedUser);
      if (this.currentUser) {
        await this.login(this.currentUser.userId);
      }
    }
  }

  async login(userId: string) {
    try {
      const response = await firstValueFrom(this.http.get<{
        token: string;
        userId: string;
        name: string;
        imageURL: string;
      }>(`${this.API_URL}/user?user_id=${userId}`));

      if (!response) {
        throw new Error('No response from server');
      }

      await StreamCall.login({
        token: response.token,
        userId: response.userId,
        name: response.name,
        imageURL: response.imageURL,
        apiKey: this.API_KEY,
        refreshToken: {
          url: `${this.API_URL}/user?user_id=${userId}`,
          headers: {
            'Content-Type': 'application/json',
          },
        },
      });

      this.currentUser = {
        userId: response.userId,
        name: response.name,
        imageURL: response.imageURL,
      };
      localStorage.setItem('currentUser', JSON.stringify(this.currentUser));
      await this.presentToast('Login successful', 'success');
    } catch (error) {
      console.error('Login failed:', error);
      await this.presentToast('Login failed', 'danger');
    }
  }

  async callUser(userId: string) {
    try {
      await StreamCall.call({
        userId: userId,
        type: 'default',
        ring: true
      });
      await this.presentToast(`Calling ${userId}...`, 'success');
    } catch (error) {
      console.error(`Failed to call ${userId}:`, error);
      await this.presentToast(`Failed to call ${userId}`, 'danger');
    }
  }

  async logout() {
    try {
      await StreamCall.logout();
      this.currentUser = null;
      localStorage.removeItem('currentUser');
      await this.presentToast('Logout successful', 'success');
    } catch (error) {
      console.error('Logout failed:', error);
      await this.presentToast('Logout failed', 'danger');
    }
  }

  closeTransparency() {
    const styleElement = document.getElementById(this.STYLE_ID);
    if (styleElement) {
      document.head.removeChild(styleElement);
    }
    this.transparent = false;
  }

  private async presentToast(message: string, color: 'success' | 'danger') {
    const toast = await this.toastController.create({
      message,
      duration: 2000,
      color,
      position: 'top'
    });
    await toast.present();
  }

  async acceptCall() {
    if (!this.incomingCallId) return;
    
    try {
      await StreamCall.acceptCall();
      await this.presentToast('Call accepted', 'success');
    } catch (error) {
      console.error('Failed to accept call:', error);
      await this.presentToast('Failed to accept call', 'danger');
    }
  }

  async rejectCall() {
    if (!this.incomingCallId) return;
    
    try {
      await StreamCall.rejectCall();
      this.incomingCallId = null;
      await this.presentToast('Call rejected', 'success');
    } catch (error) {
      console.error('Failed to reject call:', error);
      await this.presentToast('Failed to reject call', 'danger');
    }
  }

  private async presentIncomingCallToast() {
    const toast = await this.toastController.create({
      message: 'Incoming call...',
      position: 'top',
      buttons: [
        {
          side: 'start',
          icon: 'call',
          handler: () => {
            void this.acceptCall();
          }
        },
        {
          side: 'end',
          icon: 'close',
          handler: () => {
            void this.rejectCall();
          }
        }
      ],
      duration: 0
    });
    await toast.present();
  }
}
