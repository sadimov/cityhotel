import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { ServiceHotelier } from '../models/service-hotelier.model';
import { ServicesHoteliersService } from './services-hoteliers.service';

/**
 * Tests minimaux du `ServicesHoteliersService` (Tour 51bis) :
 *  - URL conforme à `environment.apiUrl` (jamais en dur)
 *  - Wrapper `ApiResponse<T>` correctement dépiéccé
 *  - `hotelId` jamais transmis (CLAUDE.md §6.1)
 *  - `page()` propage les filtres
 */
describe('ServicesHoteliersService', () => {
  let service: ServicesHoteliersService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ServicesHoteliersService],
    });
    service = TestBed.inject(ServicesHoteliersService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findById() retourne la donnée déballée du wrapper ApiResponse', () => {
    const expected: ServiceHotelier = {
      serviceId: 1,
      typeServiceId: 5,
      codeService: 'BLN-01',
      nomService: 'Blanchisserie standard',
      prixUnitaire: 200,
    };

    let actual: ServiceHotelier | undefined;
    service.findById(1).subscribe((s) => (actual = s));

    const req = httpMock.expectOne(`${environment.apiUrl}/api/inventory/services-hoteliers/1`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: expected } as ApiResponse<ServiceHotelier>);

    expect(actual).toEqual(expected);
  });

  it("page() construit les paramètres de filtres et n'envoie jamais hotelId", () => {
    const empty: PageResponse<ServiceHotelier> = {
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
      .page({ search: 'blanchisserie', typeServiceId: 3, actif: true }, 0, 10)
      .subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/inventory/services-hoteliers`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('search')).toBe('blanchisserie');
    expect(req.request.params.get('typeServiceId')).toBe('3');
    expect(req.request.params.get('actif')).toBe('true');
    expect(req.request.params.get('hotelId')).toBeNull();
    req.flush({ success: true, data: empty } as ApiResponse<PageResponse<ServiceHotelier>>);
  });

  it('create() POST vers /services-hoteliers et déballe la réponse', () => {
    const dto: ServiceHotelier = {
      typeServiceId: 5,
      codeService: 'BLN-02',
      nomService: 'Blanchisserie express',
      prixUnitaire: 350,
    };
    const expected: ServiceHotelier = { ...dto, serviceId: 42 };

    let actual: ServiceHotelier | undefined;
    service.create(dto).subscribe((s) => (actual = s));

    const req = httpMock.expectOne(`${environment.apiUrl}/api/inventory/services-hoteliers`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    // hotelId jamais envoyé
    expect((req.request.body as Record<string, unknown>)['hotelId']).toBeUndefined();
    req.flush({ success: true, data: expected } as ApiResponse<ServiceHotelier>);
    expect(actual).toEqual(expected);
  });

  it('delete() DELETE vers /services-hoteliers/{id} et résout en void', () => {
    let completed = false;
    service.delete(7).subscribe(() => (completed = true));
    const req = httpMock.expectOne(`${environment.apiUrl}/api/inventory/services-hoteliers/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true });
    expect(completed).toBeTrue();
  });
});
