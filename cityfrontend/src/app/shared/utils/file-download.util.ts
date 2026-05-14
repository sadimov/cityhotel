/**
 * Utilitaire — téléchargement d'un Blob en tant que fichier dans le navigateur.
 *
 * Pattern réutilisable pour les endpoints retournant un fichier binaire
 * (PDF facture, exports XLSX/PDF des états de synthèse, etc.).
 *
 * Pas de dépendance `file-saver` : `URL.createObjectURL` + balise `<a>` jetable
 * suffisent et fonctionnent sur tous les navigateurs modernes (cible Angular
 * 21 = ES2022, pas IE).
 */
export class FileDownloadUtil {
  /**
   * Déclenche le téléchargement d'un Blob avec le nom de fichier indiqué.
   *
   * Le Blob est libéré via `URL.revokeObjectURL` après un court délai (laisse
   * le temps au navigateur de démarrer le téléchargement).
   */
  static saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    // Délai minimal pour la libération de l'URL côté navigateur.
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  /**
   * Helper spécialisé : compose un nom de fichier `<base>-<suffix>.<ext>`
   * sanitisé (caractères ASCII safe pour les FS Windows / macOS / Linux).
   */
  static buildFilename(base: string, suffix: string, ext: string): string {
    const safe = (s: string): string =>
      s.replace(/[\\/:*?"<>|]/g, '_').replace(/\s+/g, '_');
    return `${safe(base)}-${safe(suffix)}.${ext}`;
  }
}
