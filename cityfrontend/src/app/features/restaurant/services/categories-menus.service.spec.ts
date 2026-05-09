import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { CategorieMenu } from '../models/categorie-menu.model';
import { CategoriesMenusService } from './categories-menus.service';

/**
 * Tests minimaux du `CategoriesMenusService` :
 *  - URL conforme à `environment.apiUrl` (jamais en dur)
 *  - Le wrapper `ApiResponse<T>` est dépiéccé en `T`
 *  - `hotelId` n'est jamais transmis (CLAUDE.md §6.1)
 *  - `page()` propage correctement les filtres
 */
describe('CategoriesMenusService', () => {
  let service: CategoriesMenusService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CategoriesMenusService],
    });
    service = TestBed.inject(CategoriesMenusService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findById() retourne la donnée déballée du wrapper ApiResponse', () => {
    const expected: CategorieMenu = {
      categorieId: 1,
      nomCategorie: 'Entrées',
      ordreAffichage: 1,
      actif: true,
    };

    let actual: CategorieMenu | undefined;
    service.findById(1).subscribe((c) => (actual = c));

    const req = httpMock.expectOne(`${environment.apiUrl}/api/restaurant/categories/1`);
    expect(req.request.method).toBe('GET');
    const wrapped: ApiResponse<CategorieMenu> = { success: true, data: expected };
    req.flush(wrapped);

    expect(actual).toEqual(expected);
  });

  it("page() construit les paramètres de filtres et n'envoie jamais hotelId", () => {
    const empty: PageResponse<CategorieMenu> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
      numberOfElements: 0,
      first: true,
      last: true,
    };

    service.page({ search: 'pizza', actif: true }, 0, 10).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/restaurant/categories`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('search')).toBe('pizza');
    expect(req.request.params.get('actif')).toBe('true');
    expect(req.request.params.get('hotelId')).toBeNull();
    req.flush({ success: true, data: empty } as ApiResponse<PageResponse<CategorieMenu>>);
  });

  it('findActives() GET vers /categories/actives', () => {
    service.findActives().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/restaurant/categories/actives`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] } as ApiResponse<CategorieMenu[]>);
  });

  it('delete() DELETE vers /categories/{id} et résout en void', () => {
    let completed = false;
    service.delete(7).subscribe(() => (completed = true));
    const req = httpMock.expectOne(`${environment.apiUrl}/api/restaurant/categories/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true });
    expect(completed).toBeTrue();
  });

  it("updateOrdre() PUT vers /{id}/ordre avec body { ordreAffichage }", () => {
    service.updateOrdre(5, 3).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/restaurant/categories/5/ordre`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ ordreAffichage: 3 });
    req.flush({ success: true, data: {} as CategorieMenu });
  });
});
