import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { RecapPaiementsReservationDto } from '../models/paiements-recap.model';
import { PaiementsRecapService } from './paiements-recap.service';

describe('PaiementsRecapService', () => {
  let service: PaiementsRecapService;
  let httpMock: HttpTestingController;

  const base = `${environment.apiUrl}/api/hebergement/reservations`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [PaiementsRecapService],
    });
    service = TestBed.inject(PaiementsRecapService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('appelle GET /{id}/paiements-recap et déballe `data`', () => {
    const expected: RecapPaiementsReservationDto = {
      reservationId: 12,
      factures: [
        {
          factureId: 100,
          numero: 'FACT-2026-MR-000123',
          statut: 'EMISE',
          dateFacture: '2026-05-09',
          montantTotal: 9000,
          montantPaye: 0,
          reste: 9000,
        },
      ],
      paiements: [],
      totalGlobal: 9000,
      payeGlobal: 0,
      resteGlobal: 9000,
    };
    let received: RecapPaiementsReservationDto | null = null;
    service
      .getRecapForReservation(12)
      .subscribe((r) => (received = r));

    const req = httpMock.expectOne(`${base}/12/paiements-recap`);
    expect(req.request.method).toBe('GET');
    const body: ApiResponse<RecapPaiementsReservationDto> = {
      success: true,
      message: 'ok',
      data: expected,
    };
    req.flush(body);

    expect(received).toEqual(expected);
  });
});
