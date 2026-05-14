import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { TypeServiceHotelier } from '../models/type-service-hotelier.model';
import { TypesServicesHoteliersService } from './types-services-hoteliers.service';

/**
 * Tests minimaux du `TypesServicesHoteliersService` (Tour 51bis) :
 *  - URL conforme à `environment.apiUrl` (jamais en dur)
 *  - Wrapper `ApiResponse<T>` correctement dépiéccé
 *  - `hotelId` jamais transmis (CLAUDE.md §6.1)
 */
describe('TypesServicesHoteliersService', () => {
  let service: TypesServicesHoteliersService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TypesServicesHoteliersService],
    });
    service = TestBed.inject(TypesServicesHoteliersService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findById() retourne la donnée déballée du wrapper ApiResponse', () => {
    const expected: TypeServiceHotelier = {
      typeServiceId: 1,
      codeType: 'BLN',
      nomType: 'Blanchisserie',
    };

    let actual: TypeServiceHotelier | undefined;
    service.findById(1).subscribe((t) => (actual = t));

    const req = httpMock.expectOne(`${environment.apiUrl}/api/inventory/types-services-hoteliers/1`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: expected } as ApiResponse<TypeServiceHotelier>);
    expect(actual).toEqual(expected);
  });

  it('findActifs() GET /actifs et retourne le tableau dépiéccé', () => {
    const expected: TypeServiceHotelier[] = [
      { typeServiceId: 1, codeType: 'BLN', nomType: 'Blanchisserie' },
      { typeServiceId: 2, codeType: 'NAV', nomType: 'Navette' },
    ];
    let actual: TypeServiceHotelier[] | undefined;
    service.findActifs().subscribe((arr) => (actual = arr));

    const req = httpMock.expectOne(`${environment.apiUrl}/api/inventory/types-services-hoteliers/actifs`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: expected } as ApiResponse<TypeServiceHotelier[]>);
    expect(actual).toEqual(expected);
  });

  it("create() POST sans hotelId et page() ne passe pas hotelId", () => {
    const dto: TypeServiceHotelier = { codeType: 'NEW', nomType: 'Nouveau type' };
    service.create(dto).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/inventory/types-services-hoteliers`);
    expect(req.request.method).toBe('POST');
    expect((req.request.body as Record<string, unknown>)['hotelId']).toBeUndefined();
    req.flush({ success: true, data: { ...dto, typeServiceId: 99 } });

    const empty: PageResponse<TypeServiceHotelier> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
      numberOfElements: 0,
      first: true,
      last: true,
    };
    service.page({ search: 'test', actif: false }).subscribe();
    const pageReq = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/inventory/types-services-hoteliers`,
    );
    expect(pageReq.request.params.get('search')).toBe('test');
    expect(pageReq.request.params.get('actif')).toBe('false');
    expect(pageReq.request.params.get('hotelId')).toBeNull();
    pageReq.flush({ success: true, data: empty } as ApiResponse<PageResponse<TypeServiceHotelier>>);
  });
});
