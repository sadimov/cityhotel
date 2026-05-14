import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { MontantCalculDto } from '../models/tarif-chambre.model';
import { TarifChambreService } from './tarif-chambre.service';

describe('TarifChambreService', () => {
  let service: TarifChambreService;
  let httpMock: HttpTestingController;

  const base = `${environment.apiUrl}/api/hebergement/tarifs-chambre`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TarifChambreService],
    });
    service = TestBed.inject(TarifChambreService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('appelle GET /calculer avec les bons params et déballe `data`', () => {
    const expected: MontantCalculDto = {
      typeChambreId: 7,
      totalNuits: 3,
      montantHt: 3000,
      montantTtc: 3000,
      detail: [
        { date: '2026-06-01', prix: 1000, origine: 'tarif' },
        { date: '2026-06-02', prix: 1000, origine: 'tarif' },
        { date: '2026-06-03', prix: 1000, origine: 'fallback' },
      ],
    };
    let received: MontantCalculDto | null = null;
    service.getCalcul(7, '2026-06-01', '2026-06-04').subscribe((d) => (received = d));

    const req = httpMock.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === `${base}/calculer` &&
        r.params.get('typeChambreId') === '7' &&
        r.params.get('dateDebut') === '2026-06-01' &&
        r.params.get('dateFin') === '2026-06-04',
    );
    const body: ApiResponse<MontantCalculDto> = {
      success: true,
      message: 'ok',
      data: expected,
    };
    req.flush(body);

    expect(received).toEqual(expected);
  });
});
