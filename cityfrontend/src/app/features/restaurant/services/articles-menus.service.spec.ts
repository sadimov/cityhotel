import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { ArticleMenu } from '../models/article-menu.model';
import { ArticlesMenusService } from './articles-menus.service';

/**
 * Tests minimaux du `ArticlesMenusService` :
 *  - URL conforme à `environment.apiUrl` (jamais en dur)
 *  - Le wrapper `ApiResponse<T>` est dépiéccé en `T`
 *  - `hotelId` n'est jamais transmis (CLAUDE.md §6.1)
 *  - `page()` propage correctement les filtres
 *  - `setDisponibilite()` PUT vers /{id}/disponibilite
 */
describe('ArticlesMenusService', () => {
  let service: ArticlesMenusService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ArticlesMenusService],
    });
    service = TestBed.inject(ArticlesMenusService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findById() retourne la donnée déballée du wrapper ApiResponse', () => {
    const expected: ArticleMenu = {
      articleId: 1,
      categorieId: 2,
      nomArticle: 'Pizza Margherita',
      prix: 250,
      disponible: true,
      actif: true,
    };

    let actual: ArticleMenu | undefined;
    service.findById(1).subscribe((a) => (actual = a));

    const req = httpMock.expectOne(`${environment.apiUrl}/api/restaurant/articles/1`);
    expect(req.request.method).toBe('GET');
    const wrapped: ApiResponse<ArticleMenu> = { success: true, data: expected };
    req.flush(wrapped);

    expect(actual).toEqual(expected);
  });

  it("page() construit les paramètres de filtres et n'envoie jamais hotelId", () => {
    const empty: PageResponse<ArticleMenu> = {
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
      .page({ search: 'pizza', categorieId: 3, disponible: true, actif: true }, 0, 10)
      .subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/restaurant/articles`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('search')).toBe('pizza');
    expect(req.request.params.get('categorieId')).toBe('3');
    expect(req.request.params.get('disponible')).toBe('true');
    expect(req.request.params.get('actif')).toBe('true');
    expect(req.request.params.get('hotelId')).toBeNull();
    req.flush({ success: true, data: empty } as ApiResponse<PageResponse<ArticleMenu>>);
  });

  it('findDisponibles() GET vers /articles/disponibles avec filtre catégorie', () => {
    service.findDisponibles(5).subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/restaurant/articles/disponibles`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('categorieId')).toBe('5');
    req.flush({ success: true, data: [] } as ApiResponse<ArticleMenu[]>);
  });

  it('setDisponibilite() PUT vers /{id}/disponibilite avec body { disponible }', () => {
    service.setDisponibilite(42, false).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/restaurant/articles/42/disponibilite`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ disponible: false });
    req.flush({ success: true, data: {} as ArticleMenu });
  });

  it('setRupture() PUT vers /{id}/rupture avec motif optionnel', () => {
    service.setRupture(42, 'rupture stock fromage').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/restaurant/articles/42/rupture`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ motif: 'rupture stock fromage' });
    req.flush({ success: true, data: {} as ArticleMenu });
  });

  it('delete() DELETE vers /articles/{id} et résout en void', () => {
    let completed = false;
    service.delete(7).subscribe(() => (completed = true));
    const req = httpMock.expectOne(`${environment.apiUrl}/api/restaurant/articles/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true });
    expect(completed).toBeTrue();
  });
});
