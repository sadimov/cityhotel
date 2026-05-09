import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { Nuitee } from '../models/nuitee.model';
import { NuiteesService } from './nuitees.service';

/**
 * Tests minimaux du `NuiteesService` — read-only, spec FROZEN B1+B2 :
 *  - GET `/api/hebergement/nuitees/reservation/{id}`
 *  - GET `/api/hebergement/nuitees/chambre/{id}` (paginé)
 *
 * NOTE — `facturerNuiteesReservation` retiré (Tour audit B1+B2).
 */
describe('NuiteesService', () => {
  let service: NuiteesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [NuiteesService],
    });
    service = TestBed.inject(NuiteesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findByReservation() GET /api/hebergement/nuitees/reservation/{id}', () => {
    service.findByReservation(42).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/nuitees/reservation/42`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] } as ApiResponse<Nuitee[]>);
  });

  it('findByChambreEtPeriode() GET /api/hebergement/nuitees/chambre/{id} avec params', () => {
    const empty: PageResponse<Nuitee> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 50,
      number: 0,
      numberOfElements: 0,
      first: true,
      last: true,
    };
    service.findByChambreEtPeriode(11, '2026-06-01', '2026-06-04').subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/hebergement/nuitees/chambre/11`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('dateDebut')).toBe('2026-06-01');
    expect(req.request.params.get('dateFin')).toBe('2026-06-04');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ success: true, data: empty } as ApiResponse<PageResponse<Nuitee>>);
  });
});
