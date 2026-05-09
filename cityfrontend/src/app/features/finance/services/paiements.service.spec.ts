import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { Page } from '../models/page.model';
import {
  AffectationCreateDto,
  ModePaiement,
  PaiementCreateDto,
  PaiementDto,
  StatutPaiement,
} from '../models/paiement.model';
import { PaiementsService } from './paiements.service';

/**
 * Tests minimaux du `PaiementsService` (audit B1+B2+B3 finance) :
 *  - URL conforme à `environment.apiUrl`
 *  - Pas d'enveloppe `ApiResponse<T>` — le DTO est retourné directement
 *  - Verbes HTTP alignés sur le back (POST pour les transitions)
 *  - `hotelId` jamais sérialisé dans les requêtes
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

  it('findById() retourne le PaiementDto direct', () => {
    const expected: PaiementDto = {
      paiementId: 7,
      numeroPaiement: 'PAY-2026-MR-000007',
      compteId: 3,
      montantTotal: 200,
      devise: 'MRU',
      modePaiement: ModePaiement.ESPECES,
      datePaiement: '2026-05-08',
      statut: StatutPaiement.VALIDE,
      affectations: [],
    };

    let actual: PaiementDto | undefined;
    service.findById(7).subscribe((p) => (actual = p));

    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/paiements/7`,
    );
    expect(req.request.method).toBe('GET');
    req.flush(expected);
    expect(actual).toEqual(expected);
  });

  it('page() construit les paramètres Pageable Spring Data', () => {
    const empty: Page<PaiementDto> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
      numberOfElements: 0,
      first: true,
      last: true,
    };

    service.page({ page: 1, size: 25, sort: 'datePaiement,desc' }).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/finance/paiements`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('25');
    expect(req.request.params.get('sort')).toBe('datePaiement,desc');
    expect(req.request.params.has('hotelId')).toBeFalse();
    req.flush(empty);
  });

  it('create() POST le PaiementCreateDto sans hotelId', () => {
    const dto: PaiementCreateDto = {
      factureId: 42,
      montantTotal: 100,
      modePaiement: ModePaiement.BANKILY,
      referencePaiement: 'TX-12345',
      datePaiement: '2026-05-08',
    };

    service.create(dto).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/api/finance/paiements`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    expect((req.request.body as Record<string, unknown>)['hotelId']).toBeUndefined();
    req.flush({} as PaiementDto);
  });

  it('affecter() POST la liste d\'AffectationCreateDto', () => {
    const affectations: AffectationCreateDto[] = [
      { factureId: 10, montantAffecte: 60 },
      { factureId: 11, montantAffecte: 40 },
    ];

    service.affecter(7, affectations).subscribe();

    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/paiements/7/affecter`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(affectations);
    req.flush({} as PaiementDto);
  });

  it('annuler() appelle POST /{id}/annuler (pas PUT)', () => {
    service.annuler(7).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/paiements/7/annuler`,
    );
    expect(req.request.method).toBe('POST');
    req.flush({} as PaiementDto);
  });
});
