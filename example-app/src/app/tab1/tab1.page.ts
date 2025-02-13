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

  constructor(
    private http: HttpClient,
    private toastController: ToastController
  ) {}

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
      // listen to the call event
      StreamCall.addListener('callRinging', (data) => {
        console.log('Call ringing', data);
      });
      StreamCall.addListener('callStarted', (data) => {
        console.log('Call started', data);
      });
      StreamCall.addListener('callEnded', (data) => {
        console.log('Call ended', data);
      });

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
      position: 'bottom'
    });
    await toast.present();
  }
}
