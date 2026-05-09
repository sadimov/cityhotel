import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { TypeChambre } from '../models/type-chambre.model';
import { TypesChambreService } from './types-chambre.service';

/**
 * Tests minimaux du `TypesChambreService`, alignés sur la spec FROZEN B1+B2 :
 *  - GET `/active` (et non plus `/actifs`)
 *  - POST `/{id}/deactivate` (et non plus PUT `/desactiver`)
 *  - POST `/{id}/reactivate` (et non plus PUT `/reactiver`)
 */
describe('TypesChambreService', () => {
  let service: TypesChambreService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TypesChambreService],
    });
    service = TestBed.inject(TypesChambreService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findActifs() GET /api/hebergement/types-chambres/active (spec FROZEN B1+B2)', () => {
    service.findActifs().subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/types-chambres/active`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] } as ApiResponse<TypeChambre[]>);
  });

  it('desactiver() POST /api/hebergement/types-chambres/{id}/deactivate (spec FROZEN B1+B2)', () => {
    service.desactiver(3).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/types-chambres/3/deactivate`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ success: true, data: null } as ApiResponse<void>);
  });

  it('reactiver() POST /api/hebergement/types-chambres/{id}/reactivate (spec FROZEN B1+B2)', () => {
    service.reactiver(3).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/types-chambres/3/reactivate`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ success: true, data: null } as ApiResponse<void>);
  });
});
