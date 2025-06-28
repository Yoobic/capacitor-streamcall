import { Component } from '@angular/core';
import { StreamCall } from '@capgo/capacitor-stream-call';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ToastController } from '@ionic/angular';
import { AppComponent } from '../app.component';

@Component({
  selector: 'app-tab1',
  templateUrl: 'tab1.page.html',
  styleUrls: ['tab1.page.scss'],
  standalone: false,
})
export class Tab1Page {
  private readonly STYLE_ID = 'magic_transparent_background';
  private readonly API_URL = 'https://magic-login-srvv2-48.localcan.dev';
  private readonly API_KEY = 'vq4zdsazqxd7';
  private readonly DEV_API_KEY = 'm9ueqnjut3qs'; // Replace with actual dev API key
  transparent = false;
  currentUser: {
    userId: string;
    name: string;
    imageURL: string;
    teams: string[];
  } | null = null;

  callStatus: string = 'waiting for response from SDK';
  currentEnvironment: 'normal' | 'dev' = 'normal';
  environmentText: string = 'Loading...';

  constructor(
    private http: HttpClient,
    private toastController: ToastController,
    private appComponent: AppComponent
  ) {
    void this.loadCurrentEnvironment();
    void this.loadStoredUser();
    this.getCallStatus();
  }

  private async loadCurrentEnvironment() {
    try {
      const result = await StreamCall.getDynamicStreamVideoApikey();
      if (result.hasDynamicKey) {
        this.currentEnvironment = 'dev';
        this.environmentText = 'Environment: Dev (using dynamic API key)';
      } else {
        this.currentEnvironment = 'normal';
        this.environmentText = 'Environment: Normal (using static API key)';
      }
    } catch (error) {
      console.error('Failed to get environment:', error);
      this.currentEnvironment = 'normal';
      this.environmentText = 'Environment: Unknown (error getting status)';
    }
  }

  async switchEnvironment() {
    try {
      if (this.currentEnvironment === 'normal') {
        // Switch to dev environment
        await StreamCall.setDynamicStreamVideoApikey({ apiKey: this.DEV_API_KEY });
        this.currentEnvironment = 'dev';
        this.environmentText = 'Environment: Dev (using dynamic API key)';
        await this.presentToast('Switched to Dev environment', 'success');
              } else {
          // Switch to normal environment by setting an empty dynamic key
          // This will cause getEffectiveApiKey to fall back to the static key
          await StreamCall.setDynamicStreamVideoApikey({ apiKey: '' });
          this.currentEnvironment = 'normal';
          this.environmentText = 'Environment: Normal (using static API key)';
          await this.presentToast('Switched to Normal environment', 'success');
        }
      
      // If user is logged in, show message about re-login
      if (this.currentUser) {
        await this.presentToast('Please logout and login again to use the new environment', 'danger');
      }
    } catch (error) {
      console.error('Failed to switch environment:', error);
      await this.presentToast('Failed to switch environment', 'danger');
    }
  }

  private async getCallStatus() {
    StreamCall.addListener('callEvent', (event) => {
      console.log('callEvent', event);
      if (event.callId) {
        this.callStatus = JSON.stringify(event, null, 2);
      }
    });
    try {
      this.callStatus = JSON.stringify(await StreamCall.getCallStatus(), null, 2);
    } catch (error) {
      console.error('Failed to get call status:', error);
      this.callStatus = 'Failed to get call status';
    }
  }

  private async loadStoredUser() {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      this.currentUser = JSON.parse(storedUser);
      if (this.currentUser) {
        if (this.currentUser.teams && this.currentUser.teams.length > 0) {
          await this.login(this.currentUser.userId, this.currentUser.teams[0]);
        } else {
          await this.login(this.currentUser.userId);
        }
      }
    }
  }

  async login(userId: string, team?: string) {
    try {
      const response = await firstValueFrom(this.http.get<{
        token: string;
        userId: string;
        name: string;
        imageURL: string;
        teams: string[];
      }>(`${this.API_URL}/user?user_id=${userId}${!!team ? `&team=${team}` : ''}${this.currentEnvironment === 'dev' ? '&environment=dev' : ''}`));

      if (!response) {
        throw new Error('No response from server');
      }

      // Use appropriate API key based on current environment
      const apiKeyToUse = this.currentEnvironment === 'dev' ? this.DEV_API_KEY : this.API_KEY;

      await StreamCall.login({
        token: response.token,
        userId: response.userId,
        name: response.name,
        imageURL: response.imageURL,
        apiKey: apiKeyToUse,
        magicDivId: 'call-container',
        // refreshToken: {
        //   url: `${this.API_URL}/user?user_id=${userId}`,
        //   headers: {
        //     'Content-Type': 'application/json',
        //   },
        // },
      });

      this.currentUser = {
        userId: response.userId,
        name: response.name,
        imageURL: response.imageURL,
        teams: response.teams,
      };
      localStorage.setItem('currentUser', JSON.stringify(this.currentUser));
      
      // Set current user ID in AppComponent for filtering
      this.appComponent.setCurrentUserId(response.userId);
      
      await this.presentToast(`Login successful (${this.currentEnvironment} environment)`, 'success');
      
    } catch (error) {
      console.error('Login failed:', error);
      await this.presentToast('Login failed', 'danger');
    }
  }

  async callUser(userIds: string[]) {
    try {
      await StreamCall.call({
        userIds: userIds,
        type: 'default',
        video: true,
        ring: true,
        custom: {
          invitedUsers: userIds,
        }
      });
    } catch (error) {
      console.error(`Failed to call ${userIds}:`, error);
      await this.presentToast(`Failed to call ${userIds}`, 'danger');
    }
  }

  // async callTeam(team: string) {
  //   try {
  //     await StreamCall.call({
  //       type: 'default',
  //       team: team,
  //       ring: true
  //     });
  //     await this.presentToast(`Calling team ${team}...`, 'success');
  //   } catch (error) {
  //     console.error(`Failed to call team ${team}:`, error);
  //     await this.presentToast(`Failed to call team ${team}`, 'danger');
  //   }
  // }

  async callUserWithTeam(userIds: string[], team: string) {
    try {
      await StreamCall.call({
        userIds: userIds,
        type: 'default',
        team: team, 
        ring: true
      });
      await this.presentToast(`Calling team ${team} for ${JSON.stringify(userIds)}...`, 'success');
    } catch (error) {
      console.error(`Failed to call team ${team} for ${JSON.stringify(userIds)}:`, error);
      await this.presentToast(`Failed to call team ${team} for ${JSON.stringify(userIds)}`, 'danger');
    }
  }

  async logout() {
    try {
      await StreamCall.logout();
      this.currentUser = null;
      localStorage.removeItem('currentUser');
      
      // Clear current user ID in AppComponent
      this.appComponent.setCurrentUserId('');
      
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
