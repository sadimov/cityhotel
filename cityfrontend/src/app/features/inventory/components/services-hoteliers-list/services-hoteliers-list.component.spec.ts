import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { ServiceHotelier } from '../../models/service-hotelier.model';
import { TypeServiceHotelier } from '../../models/type-service-hotelier.model';
import { ServicesHoteliersService } from '../../services/services-hoteliers.service';
import { TypesServicesHoteliersService } from '../../services/types-services-hoteliers.service';
import { ServicesHoteliersListComponent } from './services-hoteliers-list.component';

/** Smoke test (Tour 51bis) — création + load + bridge navigation. */
describe('ServicesHoteliersListComponent', () => {
  let fixture: ComponentFixture<ServicesHoteliersListComponent>;
  let component: ServicesHoteliersListComponent;
  let servicesService: jasmine.SpyObj<ServicesHoteliersService>;
  let typesService: jasmine.SpyObj<TypesServicesHoteliersService>;
  let router: jasmine.SpyObj<Router>;
  let i18n: jasmine.SpyObj<TranslationService>;

  beforeEach(async () => {
    servicesService = jasmine.createSpyObj<ServicesHoteliersService>(
      'ServicesHoteliersService',
      ['page', 'delete'],
    );
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
    servicesService.page.and.returnValue(of(empty));

    typesService = jasmine.createSpyObj<TypesServicesHoteliersService>(
      'TypesServicesHoteliersService',
      ['findActifs'],
    );
    typesService.findActifs.and.returnValue(of([] as TypeServiceHotelier[]));

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    i18n = jasmine.createSpyObj<TranslationService>('TranslationService', ['translate']);
    i18n.translate.and.returnValue('translated');

    await TestBed.configureTestingModule({
      declarations: [ServicesHoteliersListComponent],
      providers: [
        { provide: ServicesHoteliersService, useValue: servicesService },
        { provide: TypesServicesHoteliersService, useValue: typesService },
        { provide: Router, useValue: router },
        { provide: TranslationService, useValue: i18n },
      ],
    })
      .overrideTemplate(ServicesHoteliersListComponent, '')
      .compileComponents();

    fixture = TestBed.createComponent(ServicesHoteliersListComponent);
    component = fixture.componentInstance;
  });

  it('crée le composant', () => {
    expect(component).toBeTruthy();
  });

  it('load() appelle ServicesHoteliersService.page', () => {
    component.ngOnInit();
    expect(servicesService.page).toHaveBeenCalled();
    expect(component.state).toBe('empty');
  });

  it('goToBridge() navigue vers la route bridge service↔facture', () => {
    component.goToBridge();
    expect(router.navigate).toHaveBeenCalledWith(['/inventory/services/ajouter-a-facture']);
  });
});
