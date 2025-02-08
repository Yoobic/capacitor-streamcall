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
  private readonly API_URL = 'https://magic-login-srv-35.localcan.dev';
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

      await StreamCall.login({
        token: response.token,
        userId: response.userId,
        name: response.name,
        imageURL: response.imageURL
      });

      console.log('Login successful');
    } catch (error) {
      console.error('Login failed:', error);
    }
  }

  async callUser1() {
    try {
      await StreamCall.call({
        userId: 'user1',
        type: 'default',
        ring: true
      });
      console.log('Calling User 1...');
    } catch (error) {
      console.error('Failed to call User 1:', error);
    }
  }

  async callUser2() {
    try {
      await StreamCall.call({
        userId: 'user2',
        type: 'default',
        ring: true
      });
      console.log('Calling User 2...');
    } catch (error) {
      console.error('Failed to call User 2:', error);
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
