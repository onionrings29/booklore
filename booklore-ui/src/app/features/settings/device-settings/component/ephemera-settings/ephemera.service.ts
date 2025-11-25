import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../../core/config/api-config';

export interface EphemeraSettings {
  enabled: boolean;
  serverIp: string | null;
  serverPort: number | null;
  showButton: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class EphemeraService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/ephemera-settings`;
  private readonly http = inject(HttpClient);

  getSettings(): Observable<EphemeraSettings> {
    return this.http.get<EphemeraSettings>(`${this.baseUrl}`);
  }

  updateSettings(settings: EphemeraSettings): Observable<EphemeraSettings> {
    return this.http.put<EphemeraSettings>(`${this.baseUrl}`, settings);
  }
}
