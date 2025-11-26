import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../../core/config/api-config';

export interface UserEphemeraSettings {
  enabled: boolean;
  serverIp: string | null;
  serverPort: number | null;
}

@Injectable({
  providedIn: 'root'
})
export class EphemeraService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/user-ephemera-settings`;
  private readonly http = inject(HttpClient);

  getSettings(): Observable<UserEphemeraSettings> {
    return this.http.get<UserEphemeraSettings>(`${this.baseUrl}`);
  }

  updateSettings(settings: UserEphemeraSettings): Observable<UserEphemeraSettings> {
    return this.http.put<UserEphemeraSettings>(`${this.baseUrl}`, settings);
  }
}
