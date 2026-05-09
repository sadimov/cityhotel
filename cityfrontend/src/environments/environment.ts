export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/citybackend',
  appName: 'City Hotel',
  version: '1.0.0',
  defaultLanguage: 'fr' as const,
  supportedLanguages: ['fr', 'ar', 'en'] as const,
  features: {
    enableNotifications: true,
    enableRealTimeUpdates: true,
    enableDebugMode: true,
    enablePerformanceMonitoring: false
  },
  ui: {
    itemsPerPage: 10,
    maxFileSize: 5 * 1024 * 1024, // 5MB
    allowedImageTypes: ['image/jpeg', 'image/png', 'image/gif'],
    dateFormat: 'dd/MM/yyyy',
    timeFormat: 'HH:mm',
    currency: 'MRU'
  }
};