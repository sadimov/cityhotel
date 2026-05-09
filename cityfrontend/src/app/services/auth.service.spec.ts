import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { Subject } from 'rxjs';

import { AuthService } from './auth.service';
import { initialAuthState, AUTH_FEATURE_KEY } from '../store/auth/auth.state';

/**
 * Spec minimal — vérifie que `AuthService` peut s'instancier avec le store
 * NgRx mocké. Les flows métier sont testés indirectement via les effects
 * (à ajouter Vague 2).
 */
describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideMockStore({
          initialState: { [AUTH_FEATURE_KEY]: initialAuthState },
        }),
        provideMockActions(() => new Subject()),
      ],
    });
    service = TestBed.inject(AuthService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should report not authenticated when store + localStorage are empty', () => {
    localStorage.removeItem('city_hotel_token');
    localStorage.removeItem('city_hotel_user');
    expect(service.isAuthenticated()).toBeFalse();
  });
});
