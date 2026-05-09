import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { Lang, Theme } from './ui.state';

/**
 * Actions du feature store `ui`.
 */
export const UiActions = createActionGroup({
  source: 'UI',
  events: {
    // Hydratation depuis localStorage au démarrage
    'Hydrate Preferences': emptyProps(),
    'Hydrate Preferences Success': props<{
      currentLang: Lang;
      sidebarCollapsed: boolean;
    }>(),

    // Langue
    'Set Language': props<{ lang: Lang }>(),

    // Sidebar
    'Toggle Sidebar': emptyProps(),
    'Set Sidebar Collapsed': props<{ collapsed: boolean }>(),

    // Thème (réservé)
    'Set Theme': props<{ theme: Theme }>(),
  },
});
