import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import {
  PaiementGlobalResultat,
  PaiementLignesResultat,
  TransfererLignesResultat,
} from '../models/paiement-lignes.model';
import { PaiementsService } from './paiements.service';

/**
 * Tests minimaux du `PaiementsService` (Tour 45 Phase B).
 *  - POST `/api/finance/factures/paiement-lignes`
 *  - POST `/api/finance/factures/transferer-lignes`
 */
describe('PaiementsService', () => {
  let service: PaiementsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [PaiementsService],
    });
    service = TestBed.inject(PaiementsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('payerLignes() POST envoie le payload typé', () => {
    const payload = {
      lignesIds: [10, 11],
      montant: 5000,
      modePaiement: 'ESPECES' as const,
      idClient: 1,
      idCompteClient: 2,
    };
    service.payerLignes(payload).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/factures/paiement-lignes`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({
      success: true,
      data: {
        paiementId: 999,
        numeroPaiement: 'PAI-001',
        montantAffecte: 5000,
        excedent: 0,
        modePaiement: 'ESPECES',
      },
    } as ApiResponse<PaiementLignesResultat>);
  });

  it('transfererLignes() POST envoie le payload typé', () => {
    const payload = { lignesIds: [10], factureCibleId: -1 };
    service.transfererLignes(payload).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/factures/transferer-lignes`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({
      success: true,
      data: {
        factureCibleId: 42,
        factureCibleNumero: 'FACT-2026-MR-000042',
        lignesTransferees: 1,
      },
    } as ApiResponse<TransfererLignesResultat>);
  });

  it('payerGlobal() POST envoie le payload typé (Tour 46)', () => {
    const payload = {
      reservationId: 12,
      montant: 8000,
      modePaiement: 'BANKILY' as const,
      motif: 'Solde',
      idClient: 5,
      idCompteClient: 0,
    };
    service.payerGlobal(payload).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/factures/paiement-global`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({
      success: true,
      data: {
        paiementId: 777,
        numeroPaiement: 'PAY-2026-MR-000777',
        montantAffecte: 8000,
        excedent: 0,
        modePaiement: 'BANKILY',
      },
    } as ApiResponse<PaiementGlobalResultat>);
  });
});
