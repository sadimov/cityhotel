import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { Reservation, StatutReservation } from '../models/reservation.model';
import { ReservationsService } from './reservations.service';

/**
 * Tests minimaux du `ReservationsService` :
 *  - URL conforme à `environment.apiUrl` (jamais en dur)
 *  - Le wrapper `ApiResponse<T>` est bien dépiéccé en `T`
 *  - Le `hotelId` n'est jamais sérialisé dans les requêtes (CLAUDE.md §6.1)
 *  - Verbes HTTP conformes spec FROZEN B1+B2 (POST pour les transitions)
 */
describe('ReservationsService', () => {
  let service: ReservationsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ReservationsService],
    });
    service = TestBed.inject(ReservationsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findById() retourne la donnée déballée du wrapper ApiResponse', () => {
    const expected: Reservation = {
      reservationId: 42,
      clientPrincipalId: 7,
      dateArrivee: '2026-06-01',
      dateDepart: '2026-06-04',
      nbAdultes: 2,
      nbEnfants: 0,
      statut: StatutReservation.CONFIRMEE,
    };

    let actual: Reservation | undefined;
    service.findById(42).subscribe((r) => (actual = r));

    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/reservations/42`,
    );
    expect(req.request.method).toBe('GET');
    const wrapped: ApiResponse<Reservation> = { success: true, data: expected };
    req.flush(wrapped);

    expect(actual).toEqual(expected);
  });

  it("page() construit les paramètres de filtres et n'envoie jamais hotelId", () => {
    const empty: PageResponse<Reservation> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
      numberOfElements: 0,
      first: true,
      last: true,
    };

    service
      .page(
        { statut: StatutReservation.CONFIRMEE, dateArriveeDebut: '2026-05-01' },
        0,
        10,
      )
      .subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/hebergement/reservations`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('statut')).toBe('CONFIRMEE');
    expect(req.request.params.get('dateArriveeDebut')).toBe('2026-05-01');
    expect(req.request.params.get('hotelId')).toBeNull();
    req.flush({ success: true, data: empty } as ApiResponse<PageResponse<Reservation>>);
  });

  it('checkIn() POST vers /reservations/{id}/check-in sans body (spec FROZEN B1+B2)', () => {
    service.checkIn(99).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/reservations/99/check-in`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ success: true, data: {} as Reservation });
  });

  it('checkOut() POST vers /reservations/{id}/check-out sans body (spec FROZEN B1+B2)', () => {
    service.checkOut(123).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/reservations/123/check-out`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ success: true, data: {} as Reservation });
  });

  it('annuler() POST vers /reservations/{id}/cancel avec body { motif } (spec FROZEN B1+B2)', () => {
    service.annuler(77, 'client absent').subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/reservations/77/cancel`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ motif: 'client absent' });
    req.flush({ success: true, data: {} as Reservation });
  });

  it('rechercherDisponibilite() POST avec body { dateDebut, dateFin, nbPersonnes }', () => {
    service
      .rechercherDisponibilite({
        dateDebut: '2026-06-01',
        dateFin: '2026-06-04',
        nbPersonnes: 2,
      })
      .subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/reservations/rechercher-disponibilite`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      dateDebut: '2026-06-01',
      dateFin: '2026-06-04',
      nbPersonnes: 2,
    });
    req.flush({ success: true, data: { chambres: [] } });
  });
});
