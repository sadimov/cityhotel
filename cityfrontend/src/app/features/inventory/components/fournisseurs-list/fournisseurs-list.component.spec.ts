import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { Fournisseur } from '../../models/fournisseur.model';
import { FournisseursService } from '../../services/fournisseurs.service';
import { FournisseursListComponent } from './fournisseurs-list.component';

/** Smoke test (Tour 51bis) — création + load + view navigation. */
describe('FournisseursListComponent', () => {
  let fixture: ComponentFixture<FournisseursListComponent>;
  let component: FournisseursListComponent;
  let fournisseursService: jasmine.SpyObj<FournisseursService>;
  let router: jasmine.SpyObj<Router>;
  let i18n: jasmine.SpyObj<TranslationService>;

  beforeEach(async () => {
    fournisseursService = jasmine.createSpyObj<FournisseursService>('FournisseursService', [
      'page',
      'delete',
    ]);
    const empty: PageResponse<Fournisseur> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
      numberOfElements: 0,
      first: true,
      last: true,
    };
    fournisseursService.page.and.returnValue(of(empty));

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    i18n = jasmine.createSpyObj<TranslationService>('TranslationService', ['translate']);
    i18n.translate.and.returnValue('translated');

    await TestBed.configureTestingModule({
      declarations: [FournisseursListComponent],
      providers: [
        { provide: FournisseursService, useValue: fournisseursService },
        { provide: Router, useValue: router },
        { provide: TranslationService, useValue: i18n },
      ],
    })
      .overrideTemplate(FournisseursListComponent, '')
      .compileComponents();

    fixture = TestBed.createComponent(FournisseursListComponent);
    component = fixture.componentInstance;
  });

  it('crée le composant', () => {
    expect(component).toBeTruthy();
  });

  it('load() appelle FournisseursService.page', () => {
    component.ngOnInit();
    expect(fournisseursService.page).toHaveBeenCalled();
    expect(component.state).toBe('empty');
  });

  it('view() navigue vers /:id/view', () => {
    component.view({ fournisseurId: 9, nomFournisseur: 'X' });
    expect(router.navigate).toHaveBeenCalledWith(['/inventory/fournisseurs', 9, 'view']);
  });
});
