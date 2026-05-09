import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import {
  FactureCreateDto,
  FactureDto,
  StatutFacture,
  TypeFacture,
} from '../models/facture.model';
import { Page } from '../models/page.model';
import { FacturesService } from './factures.service';

/**
 * Tests minimaux du `FacturesService` (audit B1+B2+B3 finance) :
 *  - URL conforme à `environment.apiUrl` (jamais en dur)
 *  - Pas d'enveloppe `ApiResponse<T>` — le DTO est retourné directement
 *  - Verbes HTTP alignés sur le back (POST pour les transitions)
 *  - `hotelId` jamais sérialisé dans les requêtes (CLAUDE.md §6.1)
 */
describe('FacturesService', () => {
  let service: FacturesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [FacturesService],
    });
    service = TestBed.inject(FacturesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findById() retourne le FactureDto direct (pas d\'ApiResponse)', () => {
    const expected: FactureDto = {
      factureId: 42,
      numeroFacture: 'FACT-2026-MR-000042',
      typeFacture: TypeFacture.FACTURE,
      compteId: 7,
      dateFacture: '2026-05-08',
      montantHt: 100,
      montantTva: 0,
      montantTtc: 100,
      montantPaye: 0,
      montantRestant: 100,
      statut: StatutFacture.BROUILLON,
      devise: 'MRU',
      lignes: [],
    };

    let actual: FactureDto | undefined;
    service.findById(42).subscribe((f) => (actual = f));

    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/factures/42`,
    );
    expect(req.request.method).toBe('GET');
    req.flush(expected);
    expect(actual).toEqual(expected);
  });

  it('page() construit les paramètres Pageable Spring Data', () => {
    const empty: Page<FactureDto> = {
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
      .page({ page: 0, size: 10, sort: 'dateFacture,desc' })
      .subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/finance/factures`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('dateFacture,desc');
    expect(req.request.params.has('hotelId')).toBeFalse();
    req.flush(empty);
  });

  it('create() POST le FactureCreateDto sans hotelId', () => {
    const dto: FactureCreateDto = {
      typeFacture: TypeFacture.FACTURE,
      compteId: 7,
      dateFacture: '2026-05-08',
      lignes: [],
    };

    service.create(dto).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/api/finance/factures`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    expect((req.request.body as Record<string, unknown>)['hotelId']).toBeUndefined();
    req.flush({} as FactureDto);
  });

  it('emettre() appelle POST /{id}/emettre', () => {
    service.emettre(42).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/factures/42/emettre`,
    );
    expect(req.request.method).toBe('POST');
    req.flush({} as FactureDto);
  });

  it('annuler() appelle POST /{id}/annuler (pas PUT)', () => {
    service.annuler(42).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/factures/42/annuler`,
    );
    expect(req.request.method).toBe('POST');
    req.flush({} as FactureDto);
  });

  it('fromReservation() appelle POST /from-reservation/{id}', () => {
    service.fromReservation(99).subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/finance/factures/from-reservation/99`,
    );
    expect(req.request.method).toBe('POST');
    req.flush({} as FactureDto);
  });
});
