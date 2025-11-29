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

    this.ephemeraService.testConnection().subscribe({
      next: (result) => {
        this.testingConnection = false;
        if (result.success) {
          // Try to parse the response to get uptime
          let uptimeMessage = '';
          if (result.response) {
            try {
              const healthData = JSON.parse(result.response);
              if (healthData.uptime) {
                uptimeMessage = ` Uptime: ${Math.floor(healthData.uptime / 1000)}s`;
              }
            } catch (e) {
              // Ignore parse errors
            }
          }
          this.messageService.add({
            severity: 'success',
            summary: 'Connection Successful',
            detail: `Successfully connected to Ephemera server.${uptimeMessage}`
          });
        } else {
          this.messageService.add({
            severity: 'error',
            summary: 'Connection Failed',
            detail: result.message
          });
        }
      },
      error: (error) => {
        this.testingConnection = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Connection Failed',
          detail: 'Failed to test connection. Please ensure the server address and port are correct.'
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
