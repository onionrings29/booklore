import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {MessageService} from 'primeng/api';
import {EphemeraService, UserEphemeraSettings} from './ephemera.service';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {UserService} from '../../../user-management/user.service';
import {Subject} from 'rxjs';
import {debounceTime, filter, take, takeUntil} from 'rxjs/operators';
import {InputNumber} from 'primeng/inputnumber';
import {HttpClient} from '@angular/common/http';

@Component({
  selector: 'app-ephemera-settings-component',
  standalone: true,
  templateUrl: './ephemera-settings-component.html',
  styleUrl: './ephemera-settings-component.scss',
  imports: [FormsModule, Button, InputText, InputNumber],
  providers: [MessageService]
})
export class EphemeraSettingsComponent implements OnInit, OnDestroy {
  private ephemeraService = inject(EphemeraService);
  private messageService = inject(MessageService);
  protected userService = inject(UserService);
  private http = inject(HttpClient);

  private readonly destroy$ = new Subject<void>();
  private readonly settingsChange$ = new Subject<void>();

  ephemeraSettings: UserEphemeraSettings = {
    enabled: false,
    serverIp: null,
    serverPort: null
  };

  testingConnection = false;

  ngOnInit() {
    this.setupDebouncing();
    this.setupUserStateSubscription();
  }

  private setupDebouncing() {
    this.settingsChange$.pipe(
      debounceTime(500),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.saveSettings();
    });
  }

  private setupUserStateSubscription() {
    this.userService.userState$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      take(1),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.loadSettings();
    });
  }

  private loadSettings() {
    this.ephemeraService.getSettings().subscribe({
      next: (settings: UserEphemeraSettings) => {
        this.ephemeraSettings = settings;
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load Ephemera settings'});
      }
    });
  }

  onInputChange() {
    // Auto-enable when both IP and port are provided
    this.ephemeraSettings.enabled = !!(this.ephemeraSettings.serverIp && this.ephemeraSettings.serverPort);
    this.settingsChange$.next();
  }

  saveSettings() {
    // Auto-enable based on valid IP and port
    this.ephemeraSettings.enabled = !!(this.ephemeraSettings.serverIp && this.ephemeraSettings.serverPort);

    this.ephemeraService.updateSettings(this.ephemeraSettings).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Settings Saved',
          detail: 'Ephemera settings updated successfully.'
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Save Failed',
          detail: 'Failed to save Ephemera settings'
        });
      }
    });
  }

  testConnection() {
    if (!this.ephemeraSettings.serverIp || !this.ephemeraSettings.serverPort) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Missing Configuration',
        detail: 'Please provide both server address and port before testing'
      });
      return;
    }

    this.testingConnection = true;
    const healthUrl = `http://${this.ephemeraSettings.serverIp}:${this.ephemeraSettings.serverPort}/health`;

    this.http.get<{status: string, timestamp: string, uptime: number}>(healthUrl).subscribe({
      next: (response) => {
        if (response.status === 'ok') {
          this.messageService.add({
            severity: 'success',
            summary: 'Connection Successful',
            detail: `Successfully connected to Ephemera server. Uptime: ${Math.floor(response.uptime / 1000)}s`
          });
        } else {
          this.messageService.add({
            severity: 'warn',
            summary: 'Unexpected Response',
            detail: `Server responded but status is: ${response.status}`
          });
        }
        this.testingConnection = false;
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Connection Failed',
          detail: `Failed to connect to Ephemera server at ${this.ephemeraSettings.serverIp}:${this.ephemeraSettings.serverPort}. Please check your server address and ensure the server is running.`
        });
        this.testingConnection = false;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
