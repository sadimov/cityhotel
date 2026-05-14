import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import {
  NuiteeModificationDto,
  NuiteeMontantsUpdateResultat,
} from '../models/nuitee-modification.model';
import { NuiteesModifService } from './nuitees-modif.service';

/**
 * Tests minimaux du `NuiteesModifService` (Tour 45 Phase B).
 *  - GET `/api/hebergement/nuitees/reservation/{id}/provisoires`
 *  - PATCH `/api/hebergement/nuitees/montants`
 */
describe('NuiteesModifService', () => {
  let service: NuiteesModifService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [NuiteesModifService],
    });
    service = TestBed.inject(NuiteesModifService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getProvisoires() GET /provisoires', () => {
    service.getProvisoires(101).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/nuitees/reservation/101/provisoires`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] } as ApiResponse<NuiteeModificationDto[]>);
  });

  it('updateMontants() PATCH /montants envoie le payload typé', () => {
    const updates = [
      {
        nuiteeId: 1,
        nouveauMontant: 1500,
        ligneFactureId: 10,
        operationCompteId: 100,
      },
    ];
    service.updateMontants(updates).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/nuitees/montants`,
    );
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(updates);
    req.flush({
      success: true,
      data: { updatedCount: 1, totalImpact: 200 },
    } as ApiResponse<NuiteeMontantsUpdateResultat>);
  });
});
