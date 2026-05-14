import { NgModule, isDevMode, provideZoneChangeDetection } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';

// NgRx 21
import { StoreModule } from '@ngrx/store';
import { EffectsModule } from '@ngrx/effects';
import { StoreDevtoolsModule } from '@ngrx/store-devtools';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';

// Core module (HttpClient + ngx-translate)
import { CoreModule } from './core/core.module';

// Auth components
import { LoginComponent } from './auth/login/login.component';
import { AuthLayoutComponent } from './auth/auth-layout/auth-layout.component';

// Core components
import { DashboardComponent } from './core/dashboard/dashboard.component';
import { HeaderComponent } from './core/header/header.component';
import { SidebarComponent } from './core/sidebar/sidebar.component';
import { MainLayoutComponent } from './core/main-layout/main-layout.component';
import { NightAuditNotifierComponent } from './core/night-audit-notifier/night-audit-notifier.component';

// Profile components
import { ProfileComponent } from './profile/profile/profile.component';

// Services
import { AuthService } from './services/auth.service';
import { TranslationService } from './services/translation.service';
import { ChartService } from './services/chart.service';
import { PdfService } from './services/pdf.service';
import { DateUtilService } from './services/date-util.service';
import { SweetAlertService } from './services/sweetalert.service';

// Interceptors pour Angular 20 (version classe)
import { AuthInterceptor } from './interceptors/auth-interceptor.interceptor';
import { ErrorInterceptor } from './interceptors/error-interceptor.interceptor';

// Guards
import { AuthGuard } from './guards/auth-guard.guard';
import { RoleGuard } from './guards/role-guard.guard';

// Directives
import { HasRoleDirective } from './directives/has-role.directive';

// Store NgRx (root feature stores : auth + ui)
// Convention : les feature modules métier (clients, inventory, ...) câbleront
// ensuite leurs propres StoreModule.forFeature / EffectsModule.forFeature.
import { authReducer, AUTH_FEATURE_KEY, AuthEffects } from './store/auth';
import { uiReducer, UI_FEATURE_KEY, UiEffects } from './store/ui';

@NgModule({
  declarations: [
    AppComponent,

    // Auth components
    LoginComponent,
    AuthLayoutComponent,

    // Core components
    DashboardComponent,
    HeaderComponent,
    SidebarComponent,
    MainLayoutComponent,
    NightAuditNotifierComponent,

    // Profile components
    ProfileComponent,

    // Directives
    HasRoleDirective
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    BrowserAnimationsModule,
    CoreModule,
    ReactiveFormsModule,
    FormsModule,

    // ── NgRx root ────────────────────────────────────────────────────────
    StoreModule.forRoot(
      {
        [AUTH_FEATURE_KEY]: authReducer,
        [UI_FEATURE_KEY]: uiReducer,
      },
      {
        runtimeChecks: {
          strictStateImmutability: true,
          strictActionImmutability: true,
          strictStateSerializability: true,
          strictActionSerializability: true,
          strictActionWithinNgZone: true,
          strictActionTypeUniqueness: true,
        },
      },
    ),
    EffectsModule.forRoot([AuthEffects, UiEffects]),
    StoreDevtoolsModule.instrument({
      maxAge: 25,
      logOnly: !isDevMode(),
      connectInZone: true,
    }),
  ],
  providers: [
    // Zone change detection (déplacé depuis main.ts qui utilisait l'option
    // `applicationProviders` invalide sur BootstrapOptions Angular 21).
    provideZoneChangeDetection({ eventCoalescing: true }),

    // Services
    AuthService,
    TranslationService,
    ChartService,
    PdfService,
    DateUtilService,
    SweetAlertService,

    // Guards
    AuthGuard,
    RoleGuard,

    // Interceptors (version classe pour compatibilité)
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: ErrorInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
