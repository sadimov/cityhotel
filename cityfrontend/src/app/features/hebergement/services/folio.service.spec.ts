import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { FolioDto } from '../models/folio.model';
import { FolioService } from './folio.service';

/**
 * Tests minimaux du `FolioService` (Tour 46).
 *  - GET `/api/finance/comptes/client/{clientId}/folio` avec dateDebut/dateFin
 */
describe('FolioService', () => {
  let service: FolioService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [FolioService],
    });
    service = TestBed.inject(FolioService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getFolioForReservation() GET avec query params dateDebut/dateFin', () => {
    service
      .getFolioForReservation(7, '2026-05-10', '2026-05-15')
      .subscribe((folio) => {
        expect(folio.compteId).toBe(42);
        expect(folio.operations.length).toBe(1);
      });
    const req = httpMock.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === `${environment.apiUrl}/api/finance/comptes/client/7/folio`,
    );
    expect(req.request.params.get('dateDebut')).toBe('2026-05-10');
    expect(req.request.params.get('dateFin')).toBe('2026-05-15');
    req.flush({
      success: true,
      data: {
        compteId: 42,
        clientId: 7,
        clientNom: 'Doe John',
        soldeOuverture: 0,
        soldeCloture: -5000,
        totalDebits: 5000,
        totalCredits: 0,
        operations: [
          {
            operationId: 1,
            dateOperation: '2026-05-10',
            type: 'DEBIT',
            motif: 'Nuit 10/05',
            montant: 5000,
            soldeApres: -5000,
          },
        ],
      },
    } as ApiResponse<FolioDto>);
  });
});
