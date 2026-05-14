import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { TranslationService } from '../../../../services/translation.service';
import { ClientsService } from '../../../clients/services/clients.service';
import { NightAuditService } from '../../services/night-audit.service';
import { ReservationsService } from '../../services/reservations.service';
import { NightAuditPageComponent } from './night-audit-page.component';

describe('NightAuditPageComponent', () => {
  let fixture: ComponentFixture<NightAuditPageComponent>;
  let component: NightAuditPageComponent;

  let reservations: jasmine.SpyObj<ReservationsService>;
  let nightAudit: jasmine.SpyObj<NightAuditService>;
  let clients: jasmine.SpyObj<ClientsService>;
  let router: jasmine.SpyObj<Router>;
  let i18n: jasmine.SpyObj<TranslationService>;

  beforeEach(async () => {
    reservations = jasmine.createSpyObj<ReservationsService>('ReservationsService', [
      'arriveesToday',
      'departsToday',
      'checkInsEnRetard',
      'checkIn',
      'checkOut',
      'annuler',
    ]);
    reservations.arriveesToday.and.returnValue(of([]));
    reservations.departsToday.and.returnValue(of([]));
    reservations.checkInsEnRetard.and.returnValue(of([]));

    nightAudit = jasmine.createSpyObj<NightAuditService>('NightAuditService', [
      'run',
    ]);

    clients = jasmine.createSpyObj<ClientsService>('ClientsService', ['page']);
    clients.page.and.returnValue(
      of({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 200,
        number: 0,
        numberOfElements: 0,
        first: true,
        last: true,
      }),
    );

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    i18n = jasmine.createSpyObj<TranslationService>('TranslationService', [
      'translate',
    ]);
    i18n.translate.and.callFake(
      (_key: string, fallback?: string | Record<string, unknown>) =>
        typeof fallback === 'string' ? fallback : 'translated',
    );

    await TestBed.configureTestingModule({
      declarations: [NightAuditPageComponent],
      providers: [
        { provide: ReservationsService, useValue: reservations },
        { provide: NightAuditService, useValue: nightAudit },
        { provide: ClientsService, useValue: clients },
        { provide: Router, useValue: router },
        { provide: TranslationService, useValue: i18n },
      ],
    })
      .overrideComponent(NightAuditPageComponent, { set: { template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(NightAuditPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('charge les 3 listes au démarrage', () => {
    expect(reservations.checkInsEnRetard).toHaveBeenCalled();
    expect(reservations.arriveesToday).toHaveBeenCalled();
    expect(reservations.departsToday).toHaveBeenCalled();
  });

  it('passe les sections en empty quand le serveur renvoie vide', () => {
    expect(component.retardState).toBe('empty');
    expect(component.arrivalsState).toBe('empty');
    expect(component.departsState).toBe('empty');
  });

  it('navigue vers le calendrier au "back"', () => {
    component.back();
    expect(router.navigate).toHaveBeenCalledWith(['/hebergement/calendar']);
  });

  it('formate la date hôtelière au format YYYY-MM-DD', () => {
    expect(component.today).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
