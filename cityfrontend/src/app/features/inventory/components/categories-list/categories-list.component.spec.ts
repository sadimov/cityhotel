import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { CategorieProduit } from '../../models/categorie.model';
import { CategoriesService } from '../../services/categories.service';
import { CategoriesListComponent } from './categories-list.component';

/**
 * Smoke test (Tour 51bis) — création du composant + load() initial sans
 * erreur. Détaille uniquement la création + un load happy path.
 */
describe('CategoriesListComponent', () => {
  let fixture: ComponentFixture<CategoriesListComponent>;
  let component: CategoriesListComponent;
  let categoriesService: jasmine.SpyObj<CategoriesService>;
  let router: jasmine.SpyObj<Router>;
  let i18n: jasmine.SpyObj<TranslationService>;

  beforeEach(async () => {
    categoriesService = jasmine.createSpyObj<CategoriesService>('CategoriesService', [
      'page',
      'delete',
    ]);
    const empty: PageResponse<CategorieProduit> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
      numberOfElements: 0,
      first: true,
      last: true,
    };
    categoriesService.page.and.returnValue(of(empty));

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    i18n = jasmine.createSpyObj<TranslationService>('TranslationService', [
      'translate',
    ]);
    i18n.translate.and.returnValue('translated');

    await TestBed.configureTestingModule({
      declarations: [CategoriesListComponent],
      providers: [
        { provide: CategoriesService, useValue: categoriesService },
        { provide: Router, useValue: router },
        { provide: TranslationService, useValue: i18n },
      ],
    })
      .overrideTemplate(CategoriesListComponent, '')
      .compileComponents();

    fixture = TestBed.createComponent(CategoriesListComponent);
    component = fixture.componentInstance;
  });

  it('crée le composant', () => {
    expect(component).toBeTruthy();
  });

  it('load() passe en état empty quand le backend renvoie 0 éléments', () => {
    component.ngOnInit();
    expect(categoriesService.page).toHaveBeenCalled();
    expect(component.state).toBe('empty');
  });

  it('view() navigue vers la route détail /:id/view', () => {
    component.view({ categorieId: 7, codeCategorie: 'A', nomCategorie: 'A' });
    expect(router.navigate).toHaveBeenCalledWith(['/inventory/categories', 7, 'view']);
  });
});
