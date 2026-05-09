import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

@Injectable()
export class ErrorInterceptor implements HttpInterceptor {

  constructor(private authService: AuthService) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        // Gestion automatique des erreurs 401 (non autorisé)
        if (error.status === 401) {
          console.warn('🔐 Token expiré ou invalide, déconnexion automatique');
          // Token expiré ou invalide, forcer la déconnexion
          this.authService.logout().subscribe({
            next: () => console.log('✅ Déconnexion automatique suite à erreur 401'),
            error: (err) => console.error('❌ Erreur lors de la déconnexion automatique:', err)
          });
        }

        // Gestion des erreurs 403 (accès refusé)
        if (error.status === 403) {
          console.warn('🚫 Accès refusé (403) pour la requête:', request.url);
        }

        // Gestion des erreurs de connexion (0 ou 500+)
        if (error.status === 0 || error.status >= 500) {
          console.error('🔌 Erreur de connexion au serveur:', error);
        }

        return throwError(() => error);
      })
    );
  }
}