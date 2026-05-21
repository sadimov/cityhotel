/**
 * Configuration d'environnement production — déploiement serveur Windows LAN.
 *
 * <p>Cible : machine 192.168.100.141 du réseau 192.168.100.0/24 (consigne
 * 2026-05-20). Le backend Spring Boot tourne sur le port 8080 avec
 * context-path `/citybackend`. Les autres postes du réseau accèdent au
 * frontend via `http://192.168.100.141:4200`.</p>
 *
 * <p>Pour changer l'IP cible sans recompiler, soit :
 * <ul>
 *   <li>Modifier ce fichier puis `ng build --configuration production`</li>
 *   <li>Lancer `ng serve --configuration production --host 0.0.0.0
 *       --disable-host-check` (Angular utilise alors ce fichier via le
 *       `fileReplacements` d'angular.json)</li>
 * </ul></p>
 */
export const environment = {
  production: true,
  apiUrl: 'http://192.168.100.141:8080/citybackend',
  appName: 'City Hotel',
  version: '1.0.0',
  defaultLanguage: 'fr' as const,
  supportedLanguages: ['fr', 'ar', 'en'] as const,
  features: {
    enableNotifications: true,
    enableRealTimeUpdates: true,
    enableDebugMode: false,
    enablePerformanceMonitoring: true,
  },
  ui: {
    itemsPerPage: 10,
    maxFileSize: 5 * 1024 * 1024,
    allowedImageTypes: ['image/jpeg', 'image/png', 'image/gif'],
    dateFormat: 'dd/MM/yyyy',
    timeFormat: 'HH:mm',
    currency: 'MRU',
  },
};
