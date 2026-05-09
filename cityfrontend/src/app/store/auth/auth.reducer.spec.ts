import { LoginResponse } from '../../services/auth.service';
import { AuthActions } from './auth.actions';
import { authReducer } from './auth.reducer';
import { initialAuthState } from './auth.state';

/**
 * Encode un payload JSON en JWT mock (base64url, header + signature factices).
 * Utilisé pour construire un `LoginResponse.token` valide depuis lequel
 * `decodeJwt` extraira `hotelId` (Tour 7C).
 */
function buildMockJwt(payload: Record<string, unknown>): string {
  const base64url = (input: string): string =>
    btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = base64url(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('authReducer', () => {
  const fakeResponse: LoginResponse = {
    token: buildMockJwt({
      sub: '1',
      username: 'test',
      email: 'test@example.com',
      hotelId: 42,
      hotelCode: 'CITY1',
      roleCode: 'GERANT',
      iat: 1_700_000_000,
      exp: 4_070_908_800, // 2099-01-01
    }),
    tokenType: 'Bearer',
    expiryDate: '2099-01-01T00:00:00Z',
    userId: 1,
    username: 'test',
    email: 'test@example.com',
    prenom: 'Test',
    nom: 'User',
    nomComplet: 'Test User',
    hotelCode: 'CITY1',
    hotelNom: 'City Hotel 1',
    roleCode: 'GERANT',
    roleNom: 'Gérant',
    sessionId: 's1',
    derniereConnexion: '2026-05-06',
  };

  it('returns the initial state for unknown actions', () => {
    const state = authReducer(undefined, { type: '@@unknown' } as never);
    expect(state).toEqual(initialAuthState);
  });

  it('sets loading on Login action', () => {
    const next = authReducer(
      initialAuthState,
      AuthActions.login({ credentials: { username: 'a', password: 'b' } }),
    );
    expect(next.loading).toBeTrue();
    expect(next.error).toBeNull();
  });

  it('hydrates user/hotel/roles on Login Success', () => {
    const next = authReducer(
      initialAuthState,
      AuthActions.loginSuccess({ response: fakeResponse }),
    );
    expect(next.token).toBe(fakeResponse.token);
    expect(next.currentUser?.userId).toBe(1);
    expect(next.currentHotel?.hotelId).toBe(42);
    expect(next.roles).toEqual(['GERANT']);
    expect(next.loading).toBeFalse();
  });

  it('clears state on Logout Success', () => {
    const populated = authReducer(
      initialAuthState,
      AuthActions.loginSuccess({ response: fakeResponse }),
    );
    const next = authReducer(populated, AuthActions.logoutSuccess());
    expect(next).toEqual(initialAuthState);
  });

  it('stores error key (not translated text) on Login Failure', () => {
    const next = authReducer(
      initialAuthState,
      AuthActions.loginFailure({ errorKey: 'error.auth.invalidCredentials' }),
    );
    expect(next.error).toBe('error.auth.invalidCredentials');
    expect(next.loading).toBeFalse();
  });
});
