import { Component } from '@angular/core';
import { StreamCall } from 'stream-call';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

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

  constructor(private http: HttpClient) {}

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
      });

      console.log('Login successful');
    } catch (error) {
      console.error('Login failed:', error);
    }
  }

  async callUser(userId: string) {
    try {
      await StreamCall.call({
        userId: userId,
        type: 'default',
        ring: true
      });
      console.log(`Calling ${userId}...`);
    } catch (error) {
      console.error(`Failed to call ${userId}:`, error);
    }
  }

  async logout() {
    try {
      await StreamCall.logout();
      console.log('Logout successful');
    } catch (error) {
      console.error('Logout failed:', error);
    }
  }

  closeTransparency() {
    const styleElement = document.getElementById(this.STYLE_ID);
    if (styleElement) {
      document.head.removeChild(styleElement);
    }
    this.transparent = false;
  }
}
