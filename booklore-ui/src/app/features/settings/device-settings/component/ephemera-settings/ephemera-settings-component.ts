import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {MessageService} from 'primeng/api';
import {EphemeraService, EphemeraSettings} from './ephemera.service';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {UserService} from '../../../user-management/user.service';
import {Subject} from 'rxjs';
import {debounceTime, filter, take, takeUntil} from 'rxjs/operators';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {InputNumber} from 'primeng/inputnumber';

@Component({
  selector: 'app-ephemera-settings-component',
  standalone: true,
  templateUrl: './ephemera-settings-component.html',
  styleUrl: './ephemera-settings-component.scss',
  imports: [FormsModule, Button, InputText, ToggleSwitch, InputNumber],
  providers: [MessageService]
})
export class EphemeraSettingsComponent implements OnInit, OnDestroy {
  private ephemeraService = inject(EphemeraService);
  private messageService = inject(MessageService);
  protected userService = inject(UserService);

  private readonly destroy$ = new Subject<void>();
  private readonly settingsChange$ = new Subject<void>();

  isAdmin = false;

  ephemeraSettings: EphemeraSettings = {
    enabled: false,
    serverIp: null,
    serverPort: null,
    showButton: false
  };

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
    let prevIsAdmin = false;
    this.userService.userState$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      const currIsAdmin = userState.user?.permissions.admin ?? false;

      if (currIsAdmin && !prevIsAdmin) {
        this.isAdmin = true;
        this.loadSettings();
      } else {
        this.isAdmin = currIsAdmin;
      }

      prevIsAdmin = currIsAdmin;
    });
  }

  private loadSettings() {
    this.ephemeraService.getSettings().subscribe({
      next: (settings: EphemeraSettings) => {
        this.ephemeraSettings = settings;
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load Ephemera settings'});
      }
    });
  }

  onToggleChange() {
    this.settingsChange$.next();
  }

  onInputChange() {
    this.settingsChange$.next();
  }

  saveSettings() {
    this.ephemeraService.updateSettings(this.ephemeraSettings).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Settings Saved',
          detail: 'Ephemera settings updated successfully'
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
