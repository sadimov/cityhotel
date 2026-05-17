import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { FiltresHotels, Hotel } from '../models/hotel.admin.model';

/**
 * Forme exacte renvoyée par le backend `HotelAdminDto` (record Java).
 * Préfixe `hotel*` historique conservé côté serveur — on mappe vers la
 * convention front `code/nom/adresse/telephone` ici.
 */
interface HotelBackendDto {
  hotelId?: number;
  hotelCode?: string;
  hotelNom?: string;
  hotelAdresse?: string;
  hotelTel?: string;
  logoUrl?: string;
  ville?: string;
  pays?: string;
  boitePostale?: string;
  email?: string;
  siteWeb?: string;
  devise?: string;
  codePays?: string;
  fuseauHoraire?: string;
  actif?: boolean;
  dateCreation?: string;
  dateModification?: string;
}

/**
 * Service HTTP — Administration des hôtels (SUPERADMIN uniquement).
 *
 * Endpoints (cf. consigne Tour 31) :
 *   - GET    /api/admin/hotels                       — page (search, filtre actif)
 *   - GET    /api/admin/hotels/{id}                  — read
 *   - POST   /api/admin/hotels                       — create
 *   - PUT    /api/admin/hotels/{id}                  — update (code immutable serveur)
 *   - POST   /api/admin/hotels/{id}/desactiver       — soft delete
 *   - POST   /api/admin/hotels/{id}/reactiver        — restauration
 *
 * ⚠️ Aucun `hotelId` envoyé dans le body : la route est SUPERADMIN-only,
 * gardée par `SuperAdminGuard` côté front + `@PreAuthorize("hasRole('SUPERADMIN')")`
 * côté back. Le tenant context côté serveur est mis en bypass (sentinel
 * ROOT) pour ces opérations cross-hotel.
 *
 * <h3>Mapping backend ↔ front</h3>
 * Le DTO backend utilise des champs préfixés `hotel*` historiques
 * (`hotelCode`, `hotelNom`, `hotelAdresse`, `hotelTel`). Le modèle TS front
 * utilise une convention courte (`code`, `nom`, `adresse`, `telephone`) sur
 * laquelle 7+ composants dépendent (hotels-list, hotel-form, users-list,
 * user-form filter+select, etc.). On mappe une seule fois ici via
 * `toFrontHotel` / `toBackendDto` pour ne pas toucher tous les composants.
 */
@Injectable({ providedIn: 'root' })
export class HotelsAdminService {
  private readonly base = `${environment.apiUrl}/api/admin/hotels`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresHotels = {},
    page = 0,
    size = 10,
    sortBy = 'nom',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<Hotel>> {
    const sortField = this.toBackendSortField(sortBy);
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      // Spring Pageable standard : `sort=field,dir`.
      .set('sort', `${sortField},${sortDir}`);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.actif !== undefined) {
      params = params.set('actif', String(filtres.actif));
    }
    return this.http
      .get<ApiResponse<PageResponse<HotelBackendDto>>>(this.base, { params })
      .pipe(
        map((r) => r.data as PageResponse<HotelBackendDto>),
        map((p) => ({
          ...p,
          content: (p.content ?? []).map((dto) => this.toFrontHotel(dto)),
        }) as PageResponse<Hotel>),
      );
  }

  findById(id: number): Observable<Hotel> {
    return this.http
      .get<ApiResponse<HotelBackendDto>>(`${this.base}/${id}`)
      .pipe(map((r) => this.toFrontHotel(r.data as HotelBackendDto)));
  }

  create(dto: Hotel): Observable<Hotel> {
    return this.http
      .post<ApiResponse<HotelBackendDto>>(this.base, this.toBackendDto(dto))
      .pipe(map((r) => this.toFrontHotel(r.data as HotelBackendDto)));
  }

  update(id: number, dto: Hotel): Observable<Hotel> {
    return this.http
      .put<ApiResponse<HotelBackendDto>>(
        `${this.base}/${id}`,
        this.toBackendDto(dto),
      )
      .pipe(map((r) => this.toFrontHotel(r.data as HotelBackendDto)));
  }

  desactiver(id: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${id}/desactiver`, {})
      .pipe(map(() => undefined));
  }

  reactiver(id: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${id}/reactiver`, {})
      .pipe(map(() => undefined));
  }

  // ────────────────────────────────────────────────────────────────────────
  // Mapping privé backend ↔ front
  // ────────────────────────────────────────────────────────────────────────

  private toFrontHotel(dto: HotelBackendDto | null | undefined): Hotel {
    if (!dto) {
      return { code: '', nom: '' };
    }
    return {
      hotelId: dto.hotelId,
      code: dto.hotelCode ?? '',
      nom: dto.hotelNom ?? '',
      adresse: dto.hotelAdresse,
      telephone: dto.hotelTel,
      ville: dto.ville,
      pays: dto.pays,
      email: dto.email,
      siteWeb: dto.siteWeb,
      dateCreation: dto.dateCreation,
      actif: dto.actif,
    };
  }

  private toBackendDto(hotel: Hotel): HotelBackendDto {
    return {
      hotelId: hotel.hotelId,
      hotelCode: hotel.code,
      hotelNom: hotel.nom,
      hotelAdresse: hotel.adresse,
      hotelTel: hotel.telephone,
      ville: hotel.ville,
      pays: hotel.pays,
      email: hotel.email,
      siteWeb: hotel.siteWeb,
      actif: hotel.actif,
    };
  }

  /** Conversion sortBy front → champ entité backend (préfixe `hotel*`). */
  private toBackendSortField(field: string): string {
    switch (field) {
      case 'nom': return 'hotelNom';
      case 'code': return 'hotelCode';
      case 'adresse': return 'hotelAdresse';
      case 'telephone': return 'hotelTel';
      default: return field;
    }
  }
}
