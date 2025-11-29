import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DomSanitizer, SafeResourceUrl} from '@angular/platform-browser';
import {API_CONFIG} from '../../core/config/api-config';
import {PageTitleService} from '../../shared/service/page-title.service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {EphemeraService} from '../settings/device-settings/component/ephemera-settings/ephemera.service';
import {Router} from '@angular/router';
import {Button} from 'primeng/button';

@Component({
  selector: 'app-ephemera',
  standalone: true,
  imports: [CommonModule, ProgressSpinner, Button],
  templateUrl: './ephemera.component.html',
  styleUrl: './ephemera.component.scss'
})
export class EphemeraComponent implements OnInit, OnDestroy {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly pageTitle = inject(PageTitleService);
  private readonly ephemeraService = inject(EphemeraService);
  private readonly router = inject(Router);

  iframeUrl?: SafeResourceUrl;
  isLoading = true;
  isConfigured = false;

  ngOnInit(): void {
    this.pageTitle.setPageTitle('Ephemera');
    this.checkConfiguration();
  }

  private checkConfiguration(): void {
    this.ephemeraService.getSettings().subscribe({
      next: (settings) => {
        this.isConfigured = !!(settings?.serverIp && settings?.serverPort);
        if (this.isConfigured) {
          const ephemeraUrl = `${API_CONFIG.BASE_URL}/api/v1/ephemera/`;
          this.iframeUrl = this.sanitizer.bypassSecurityTrustResourceUrl(ephemeraUrl);
        } else {
          this.isLoading = false;
        }
      },
      error: () => {
        this.isConfigured = false;
        this.isLoading = false;
      }
    });
  }

  navigateToSettings(): void {
    this.router.navigate(['/settings'], { fragment: 'ephemera' });
  }

  ngOnDestroy(): void {
    this.isLoading = false;
  }

  handleLoad(): void {
    this.isLoading = false;
  }
}

