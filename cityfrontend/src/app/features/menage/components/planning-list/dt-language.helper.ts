/**
 * Helper minimal — fournit l'objet `language` attendu par DataTables 2.x
 * en fonction du code langue ngx-translate (`fr` | `ar` | `en`).
 *
 * Premier usage de DataTables direct du projet (Tour 28). On garde le
 * helper local au composant `planning-list/` ; lorsqu'un second
 * composant adoptera DataTables direct, ce helper sera promu vers
 * `shared/utils/dt-language.helper.ts`.
 *
 * Les libellés sont volontairement courts et autoportants — on évite
 * d'invoquer ngx-translate ici car DataTables consomme l'objet une
 * seule fois à l'init et notre logique de pagination/recherche client
 * reste cohérente avec les listes Bootstrap natives du module.
 *
 * Le type retourné est typé via `ConfigLanguage` de `@types/datatables.net`
 * (DataTables 2.x). On évite donc une interface maison qui dérive et
 * ferait diverger les casses d'options (`aria.orderable` vs
 * `aria.sortAscending` historique).
 */
import type { ConfigLanguage } from 'datatables.net';

export type DtLangCode = 'fr' | 'ar' | 'en';

export type DtLanguage = ConfigLanguage;

const LANG_FR: DtLanguage = {
  emptyTable: 'Aucune donnée disponible',
  info: 'Affichage de _START_ à _END_ sur _TOTAL_ entrées',
  infoEmpty: 'Affichage de 0 à 0 sur 0 entrée',
  infoFiltered: '(filtré sur _MAX_ entrées au total)',
  lengthMenu: 'Afficher _MENU_ entrées',
  loadingRecords: 'Chargement...',
  processing: 'Traitement...',
  search: 'Rechercher :',
  zeroRecords: 'Aucun résultat',
  paginate: {
    first: 'Premier',
    last: 'Dernier',
    next: 'Suivant',
    previous: 'Précédent',
  },
};

const LANG_AR: DtLanguage = {
  emptyTable: 'لا توجد بيانات',
  info: 'عرض _START_ إلى _END_ من أصل _TOTAL_ سجل',
  infoEmpty: 'عرض 0 إلى 0 من أصل 0 سجل',
  infoFiltered: '(مُرشَّح من إجمالي _MAX_ سجل)',
  lengthMenu: 'عرض _MENU_ سجل',
  loadingRecords: 'جاري التحميل...',
  processing: 'جاري المعالجة...',
  search: 'بحث:',
  zeroRecords: 'لا توجد نتائج',
  paginate: {
    first: 'الأول',
    last: 'الأخير',
    next: 'التالي',
    previous: 'السابق',
  },
};

const LANG_EN: DtLanguage = {
  emptyTable: 'No data available',
  info: 'Showing _START_ to _END_ of _TOTAL_ entries',
  infoEmpty: 'Showing 0 to 0 of 0 entries',
  infoFiltered: '(filtered from _MAX_ total entries)',
  lengthMenu: 'Show _MENU_ entries',
  loadingRecords: 'Loading...',
  processing: 'Processing...',
  search: 'Search:',
  zeroRecords: 'No matching records',
  paginate: {
    first: 'First',
    last: 'Last',
    next: 'Next',
    previous: 'Previous',
  },
};

/**
 * Retourne l'objet `language` à passer à DataTables selon la langue
 * courante. Retombe sur `fr` si la langue n'est pas reconnue.
 */
export function dtLang(lang: string | undefined | null): DtLanguage {
  switch (lang) {
    case 'ar':
      return LANG_AR;
    case 'en':
      return LANG_EN;
    case 'fr':
    default:
      return LANG_FR;
  }
}
