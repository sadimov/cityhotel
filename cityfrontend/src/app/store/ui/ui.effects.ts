import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { withLatestFrom } from 'rxjs';
import { map, tap } from 'rxjs/operators';

import { UiActions } from './ui.actions';
import { selectSidebarCollapsed } from './ui.selectors';
import {
  DEFAULT_LANG,
  Lang,
  SUPPORTED_LANGS,
} from './ui.state';

const LANG_STORAGE_KEY = 'city_hotel_lang';
const SIDEBAR_STORAGE_KEY = 'city_hotel_sidebar_collapsed';

/**
 * Effects du feature store `ui` : persistance localStorage opt-in pour la
 * langue et l'état de la sidebar. Isolé ici pour ne pas mélanger avec un
 * meta-reducer global qui cumulerait des responsabilités.
 *
 * Note : la synchronisation `<html dir="rtl">` côté DOM est volontairement
 * laissée au composant racine `AppComponent` (qui s'abonne à `selectIsRtl`)
 * — pas dans un effect, car toucher au DOM dans un effect est un anti-pattern
 * NgRx (sauf cas justifié comme la nav router).
 */
@Injectable()
export class UiEffects {
  private readonly actions$ = inject(Actions);
  private readonly store = inject(Store);

  hydratePreferences$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UiActions.hydratePreferences),
      map(() => {
        const storedLang = this.readLang();
        const storedSidebar = this.readSidebar();
        return UiActions.hydratePreferencesSuccess({
          currentLang: storedLang,
          sidebarCollapsed: storedSidebar,
        });
      }),
    ),
  );

  persistLang$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(UiActions.setLanguage),
        tap(({ lang }) => this.writeLang(lang)),
      ),
    { dispatch: false },
  );

  /**
   * Persiste l'état de la sidebar à chaque changement (toggle ou set explicite).
   * On lit `selectSidebarCollapsed` APRÈS que le reducer ait mis à jour le store
   * grâce à `withLatestFrom`.
   */
  persistSidebar$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(UiActions.toggleSidebar, UiActions.setSidebarCollapsed),
        withLatestFrom(this.store.select(selectSidebarCollapsed)),
        tap(([, collapsed]) => this.writeSidebar(collapsed)),
      ),
    { dispatch: false },
  );

  private readLang(): Lang {
    if (typeof localStorage === 'undefined') {
      return DEFAULT_LANG;
    }
    const stored = localStorage.getItem(LANG_STORAGE_KEY);
    if (stored && (SUPPORTED_LANGS as ReadonlyArray<string>).includes(stored)) {
      return stored as Lang;
    }
    return DEFAULT_LANG;
  }

  private writeLang(lang: Lang): void {
    if (typeof localStorage === 'undefined') {
      return;
    }
    localStorage.setItem(LANG_STORAGE_KEY, lang);
  }

  private readSidebar(): boolean {
    if (typeof localStorage === 'undefined') {
      return false;
    }
    return localStorage.getItem(SIDEBAR_STORAGE_KEY) === 'true';
  }

  private writeSidebar(collapsed: boolean): void {
    if (typeof localStorage === 'undefined') {
      return;
    }
    localStorage.setItem(SIDEBAR_STORAGE_KEY, String(collapsed));
  }
}
