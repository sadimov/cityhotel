import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';

import { AuthService } from '../../services/auth.service';
import { TranslationService } from '../../services/translation.service';
import {
  NIGHT_AUDIT_EVENT_TYPES,
  NightAuditEvent,
} from '../../features/hebergement/models/night-audit.model';
import { NightAuditNotificationsService } from '../services/night-audit-notifications.service';
import { NightAuditNotifierComponent } from './night-audit-notifier.component';

describe('NightAuditNotifierComponent', () => {
  let fixture: ComponentFixture<NightAuditNotifierComponent>;
  let component: NightAuditNotifierComponent;
  let eventsSubject: Subject<NightAuditEvent>;

  let notifications: jasmine.SpyObj<NightAuditNotificationsService>;
  let auth: jasmine.SpyObj<AuthService>;
  let i18n: jasmine.SpyObj<TranslationService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    eventsSubject = new Subject<NightAuditEvent>();
    notifications = jasmine.createSpyObj<NightAuditNotificationsService>(
      'NightAuditNotificationsService',
      ['connect', 'disconnect'],
      { events$: eventsSubject.asObservable() },
    );
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['hasAnyRole']);
    auth.hasAnyRole.and.returnValue(true);
    i18n = jasmine.createSpyObj<TranslationService>('TranslationService', [
      'translate',
    ]);
    i18n.translate.and.callFake(
      (_key: string, fallback?: string | Record<string, unknown>) =>
        typeof fallback === 'string' ? fallback : 'translated',
    );
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      declarations: [NightAuditNotifierComponent],
      providers: [
        { provide: NightAuditNotificationsService, useValue: notifications },
        { provide: AuthService, useValue: auth },
        { provide: TranslationService, useValue: i18n },
        { provide: Router, useValue: router },
      ],
      schemas: [],
    })
      .overrideComponent(NightAuditNotifierComponent, {
        set: { template: '' },
      })
      .compileComponents();

    // S'assure d'un état propre du localStorage entre tests.
    try {
      if (typeof localStorage !== 'undefined') {
        const keys: string[] = [];
        for (let i = 0; i < localStorage.length; i++) {
          const k = localStorage.key(i);
          if (k && k.startsWith('night_audit_snoozed_')) keys.push(k);
        }
        keys.forEach((k) => localStorage.removeItem(k));
      }
    } catch {
      /* ignore */
    }

    fixture = TestBed.createComponent(NightAuditNotifierComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se connecte au flux SSE au démarrage', () => {
    expect(notifications.connect).toHaveBeenCalled();
  });

  it('se déconnecte lors de la destruction', () => {
    fixture.destroy();
    expect(notifications.disconnect).toHaveBeenCalled();
  });

  it('ignore les events inconnus', () => {
    eventsSubject.next({
      eventName: 'night-audit-alert',
      type: 'OTHER',
      message: 'x',
    });
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it("n'ouvre pas la modale si l'utilisateur n'a pas le rôle", () => {
    auth.hasAnyRole.and.returnValue(false);
    eventsSubject.next({
      eventName: 'night-audit-modal',
      type: NIGHT_AUDIT_EVENT_TYPES.OPEN_LAUNCH_MODAL,
      message: 'x',
    });
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('"Démarrer" navigue vers /hebergement/night-audit', () => {
    component.start();
    expect(router.navigate).toHaveBeenCalledWith(['/hebergement/night-audit']);
  });

  it('"Reporter" pose le flag localStorage pour la journée courante', () => {
    component.snooze();
    const keys: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith('night_audit_snoozed_')) keys.push(k);
    }
    expect(keys.length).toBe(1);
    expect(localStorage.getItem(keys[0])).toBe('1');
  });
});
