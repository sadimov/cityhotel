import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { Page } from '../../finance/models/page.model';
import { ExerciceDto, StatutExercice } from '../models/exercice.model';
import { ExercicesService } from './exercices.service';

/**
 * Test minimal du service Exercices (B7) — vérifie que les URL et méthodes
 * HTTP sont correctement câblées sur les endpoints `/api/finance/exercices`
 * et que les DTOs typés sont bien retournés.
 */
describe('ExercicesService', () => {
  let service: ExercicesService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiUrl}/api/finance/exercices`;

  const sample: ExerciceDto = {
    id: 1,
    code: '2026',
    dateDebut: '2026-01-01',
    dateFin: '2026-12-31',
    statut: StatutExercice.OUVERT,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ExercicesService],
    });
    service = TestBed.inject(ExercicesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findById appelle GET /exercices/{id}', () => {
    service.findById(1).subscribe((ex) => expect(ex).toEqual(sample));
    const req = httpMock.expectOne(`${base}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(sample);
  });

  it('current appelle GET /exercices/current', () => {
    service.current().subscribe((ex) => expect(ex.code).toBe('2026'));
    const req = httpMock.expectOne(`${base}/current`);
    expect(req.request.method).toBe('GET');
    req.flush(sample);
  });

  it('page transmet les paramètres Spring Data', () => {
    const fakePage: Page<ExerciceDto> = {
      content: [sample],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      number: 0,
      numberOfElements: 1,
      first: true,
      last: true,
    };
    service.page({ page: 0, size: 10, sort: 'dateDebut,desc' }).subscribe();
    const req = httpMock.expectOne((r) =>
      r.url === base
      && r.params.get('page') === '0'
      && r.params.get('size') === '10'
      && r.params.get('sort') === 'dateDebut,desc',
    );
    expect(req.request.method).toBe('GET');
    req.flush(fakePage);
  });

  it('cloturer appelle POST /exercices/{id}/cloturer', () => {
    service.cloturer(1).subscribe();
    const req = httpMock.expectOne(`${base}/1/cloturer`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...sample, statut: StatutExercice.CLOTURE });
  });
});
