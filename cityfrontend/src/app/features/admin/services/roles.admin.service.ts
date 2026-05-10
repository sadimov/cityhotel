import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { Role } from '../models/role.admin.model';

/**
 * Service HTTP — Référentiel des rôles (lecture seule).
 *
 * Endpoint (cf. consigne Tour 31) :
 *   - GET /api/admin/roles — liste complète (référentiel court, pas paginé)
 *
 * Les rôles sont seedés par migration Liquibase et ne sont pas éditables
 * via l'UI (pas de POST/PUT/DELETE). Cf. `roles_utilisateurs.txt`.
 */
@Injectable({ providedIn: 'root' })
export class RolesAdminService {
  private readonly base = `${environment.apiUrl}/api/admin/roles`;

  constructor(private readonly http: HttpClient) {}

  findAll(): Observable<Role[]> {
    return this.http
      .get<ApiResponse<Role[]>>(this.base)
      .pipe(map((r) => r.data ?? []));
  }
}
