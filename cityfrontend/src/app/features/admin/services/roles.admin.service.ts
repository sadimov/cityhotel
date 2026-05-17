import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { Role } from '../models/role.admin.model';

/**
 * Forme exacte renvoyée par le backend `RoleAdminDto` (record Java).
 * Mêmes préfixes historiques que `HotelAdminDto` — mapping vers le modèle
 * TS court fait ici.
 */
interface RoleBackendDto {
  roleId?: number;
  roleCode?: string;
  roleNom?: string;
  description?: string;
  /** Sérialisation backend : JSON `["PERM_A","PERM_B"]` ou CSV `PERM_A,PERM_B`. */
  permissions?: string | string[];
  actif?: boolean;
}

/**
 * Service HTTP — Référentiel des rôles (lecture seule).
 *
 * Endpoint (cf. consigne Tour 31) :
 *   - GET /api/admin/roles — liste complète (référentiel court, pas paginé)
 *
 * Les rôles sont seedés par migration Liquibase et ne sont pas éditables
 * via l'UI (pas de POST/PUT/DELETE). Cf. `roles_utilisateurs.txt`.
 *
 * <h3>Mapping backend ↔ front</h3>
 * Le DTO backend utilise `roleCode`/`roleNom` (préfixe historique) ; le
 * modèle TS front utilise `code`/`nom`. Mapping local ici pour ne pas
 * toucher les 5+ composants qui consomment `Role`.
 */
@Injectable({ providedIn: 'root' })
export class RolesAdminService {
  private readonly base = `${environment.apiUrl}/api/admin/roles`;

  constructor(private readonly http: HttpClient) {}

  findAll(): Observable<Role[]> {
    return this.http
      .get<ApiResponse<RoleBackendDto[]>>(this.base)
      .pipe(map((r) => (r?.data ?? []).map((dto) => this.toFrontRole(dto))));
  }

  private toFrontRole(dto: RoleBackendDto): Role {
    return {
      roleId: dto.roleId ?? 0,
      code: dto.roleCode ?? '',
      nom: dto.roleNom ?? '',
      description: dto.description,
      permissions: this.parsePermissions(dto.permissions),
    };
  }

  /**
   * Tolère 3 formats de sérialisation des permissions :
   *  - Array déjà parsé (`string[]`)
   *  - JSON stringifié (`'["PERM_A","PERM_B"]'`)
   *  - CSV simple (`'PERM_A,PERM_B'`)
   * Retourne `undefined` si null/vide.
   */
  private parsePermissions(raw: string | string[] | undefined): string[] | undefined {
    if (!raw) return undefined;
    if (Array.isArray(raw)) return raw;
    const trimmed = raw.trim();
    if (!trimmed) return undefined;
    if (trimmed.startsWith('[')) {
      try {
        const parsed = JSON.parse(trimmed);
        if (Array.isArray(parsed)) return parsed.filter((p) => typeof p === 'string');
      } catch {
        /* fallback CSV */
      }
    }
    return trimmed
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }
}
