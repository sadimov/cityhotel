import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { Produit } from '../models/produit.model';
import { ProduitsService } from './produits.service';

/**
 * Tests minimaux du `ProduitsService` :
 *  - URL conforme à `environment.apiUrl` (jamais en dur)
 *  - Le wrapper `ApiResponse<T>` est dépiéccé en `T`
 *  - `hotelId` n'est jamais transmis (CLAUDE.md §6.1)
 *  - `page()` propage correctement les filtres
 *  - `ajusterStock()` POST vers /{id}/ajuster-stock
 */
describe('ProduitsService', () => {
  let service: ProduitsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ProduitsService],
    });
    service = TestBed.inject(ProduitsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('findById() retourne la donnée déballée du wrapper ApiResponse', () => {
    const expected: Produit = {
      produitId: 1,
      codeProduit: 'WTR-001',
      nomProduit: 'Bouteille eau 1L',
      categorieId: 5,
      uniteMesure: 'bouteille',
      prixUnitaire: 50,
      seuilAlerte: 20,
      seuilCritique: 5,
      stockActuel: 80,
    };

    let actual: Produit | undefined;
    service.findById(1).subscribe((p) => (actual = p));

    const req = httpMock.expectOne(`${environment.apiUrl}/api/inventory/produits/1`);
    expect(req.request.method).toBe('GET');
    const wrapped: ApiResponse<Produit> = { success: true, data: expected };
    req.flush(wrapped);

    expect(actual).toEqual(expected);
  });

  it("page() construit les paramètres de filtres et n'envoie jamais hotelId", () => {
    const empty: PageResponse<Produit> = {
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
      .page({ search: 'eau', categorieId: 3, statutStock: 'ALERTE' }, 0, 10)
      .subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/api/inventory/produits`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('search')).toBe('eau');
    expect(req.request.params.get('categorieId')).toBe('3');
    expect(req.request.params.get('statutStock')).toBe('ALERTE');
    expect(req.request.params.get('hotelId')).toBeNull();
    req.flush({ success: true, data: empty } as ApiResponse<PageResponse<Produit>>);
  });

  it('ajusterStock() POST vers /produits/{id}/ajuster-stock', () => {
    service
      .ajusterStock(42, {
        produitId: 42,
        nouveauStock: 100,
        raisonAjustement: 'inventaire physique',
      })
      .subscribe();
    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/inventory/produits/42/ajuster-stock`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      produitId: 42,
      nouveauStock: 100,
      raisonAjustement: 'inventaire physique',
    });
    req.flush({ success: true, data: {} as Produit });
  });

  it('delete() DELETE vers /produits/{id} et résout en void', () => {
    let completed = false;
    service.delete(7).subscribe(() => (completed = true));
    const req = httpMock.expectOne(`${environment.apiUrl}/api/inventory/produits/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true });
    expect(completed).toBeTrue();
  });
});
