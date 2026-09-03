import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class TelegramService {
  private apiUrl = 'http://localhost:8080/api/telegram';

  constructor(private http: HttpClient) {}

  testAlert(message: string): Observable<string> {
    return this.http.post(`${this.apiUrl}/test`, { message }, { responseType: 'text' });
  }
}
