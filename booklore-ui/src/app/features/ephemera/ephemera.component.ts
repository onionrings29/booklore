import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DomSanitizer, SafeResourceUrl} from '@angular/platform-browser';
import {API_CONFIG} from '../../core/config/api-config';
import {PageTitleService} from '../../shared/service/page-title.service';
import {ProgressSpinner} from 'primeng/progressspinner';

@Component({
  selector: 'app-ephemera',
  standalone: true,
  imports: [CommonModule, ProgressSpinner],
  templateUrl: './ephemera.component.html',
  styleUrl: './ephemera.component.scss'
})
export class EphemeraComponent implements OnInit, OnDestroy {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly pageTitle = inject(PageTitleService);

  iframeUrl?: SafeResourceUrl;
  isLoading = true;

  ngOnInit(): void {
    this.pageTitle.setPageTitle('Ephemera');
    const ephemeraUrl = `${API_CONFIG.BASE_URL}/api/v1/ephemera/`;
    this.iframeUrl = this.sanitizer.bypassSecurityTrustResourceUrl(ephemeraUrl);
  }

  ngOnDestroy(): void {
    this.isLoading = false;
  }

  handleLoad(): void {
    this.isLoading = false;
  }
}

