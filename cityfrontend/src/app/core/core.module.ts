import { NgModule, Optional, SkipSelf } from '@angular/core';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';

/**
 * Factory pour le loader de traductions HTTP de ngx-translate.
 * Charge les fichiers JSON depuis `assets/i18n/{lang}.json`.
 */
export function HttpLoaderFactory(http: HttpClient): TranslateHttpLoader {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}

/**
 * CoreModule — singletons techniques (HTTP, i18n).
 * À importer UNE SEULE FOIS dans AppModule.
 */
@NgModule({
  imports: [
    HttpClientModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient],
      },
      defaultLanguage: 'fr',
    }),
  ],
  exports: [
    HttpClientModule,
    TranslateModule,
  ],
})
export class CoreModule {
  constructor(@Optional() @SkipSelf() parentModule?: CoreModule) {
    if (parentModule) {
      throw new Error(
        'CoreModule a déjà été chargé. Importe-le uniquement dans AppModule.'
      );
    }
  }
}
