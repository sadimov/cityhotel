import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { Chambre, StatutChambre } from '../models/chambre.model';
import { ChambresService } from './chambres.service';

/**
 * Tests minimaux du `ChambresService`, alignés sur la spec FROZEN B1+B2
 * (2026-05-07) :
 *  - GET `/active` (et non plus `/actives`)
 *  - POST `/{id}/deactivate` (et non plus PUT `/desactiver`)
 *  - POST `/{id}/reactivate` (et non plus PUT `/reactiver`)
 *  - GET `/disponibles?dateDebut&dateFin`
 */
describe('ChambresService', () => {
  let service: ChambresService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ChambresService],
    });
    service = TestBed.inject(ChambresService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findActives() GET /api/hebergement/chambres/active (spec FROZEN B1+B2)', () => {
    service.findActives().subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/chambres/active`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] } as ApiResponse<Chambre[]>);
  });

  it("page() n'envoie jamais hotelId dans les params", () => {
    const empty: PageResponse<Chambre> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
      numberOfElements: 0,
      first: true,
      last: true,
    };
    service.page({ statut: StatutChambre.DISPONIBLE }, 0, 10).subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/hebergement/chambres`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('statut')).toBe('DISPONIBLE');
    expect(req.request.params.get('hotelId')).toBeNull();
    req.flush({ success: true, data: empty } as ApiResponse<PageResponse<Chambre>>);
  });

  it('disponibles(dateDebut, dateFin) GET /disponibles avec query params', () => {
    service.disponibles('2026-06-01', '2026-06-04').subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/hebergement/chambres/disponibles`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('dateDebut')).toBe('2026-06-01');
    expect(req.request.params.get('dateFin')).toBe('2026-06-04');
    req.flush({ success: true, data: [] } as ApiResponse<Chambre[]>);
  });

  it('desactiver() POST /api/hebergement/chambres/{id}/deactivate (spec FROZEN B1+B2)', () => {
    service.desactiver(7).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/chambres/7/deactivate`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ success: true, data: null } as ApiResponse<void>);
  });

  it('reactiver() POST /api/hebergement/chambres/{id}/reactivate (spec FROZEN B1+B2)', () => {
    service.reactiver(7).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/chambres/7/reactivate`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ success: true, data: null } as ApiResponse<void>);
  });

  it('changerStatut() PUT /api/hebergement/chambres/{id}/statut?statut=...', () => {
    service.changerStatut(11, StatutChambre.OCCUPEE).subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/hebergement/chambres/11/statut`,
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.params.get('statut')).toBe('OCCUPEE');
    req.flush({ success: true, data: {} as Chambre });
  });
});
