import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateModule } from '@ngx-translate/core';

import { PosComponent } from './pos.component';
import { ArticleGridComponent } from './article-grid/article-grid.component';
import { CartComponent } from './cart/cart.component';
import { ClientSearchComponent } from './client-search/client-search.component';
import { PaymentModalComponent } from './payment-modal/payment-modal.component';

/**
 * Test smoke minimal : monte le composant POS avec ses dépendances HTTP / I18N
 * stubs. Vérifie la création + l'absence d'erreur de template. Les tests
 * détaillés du store sont à ajouter ultérieurement (`pos.store.spec.ts`).
 */
describe('PosComponent', () => {
  let fixture: ComponentFixture<PosComponent>;
  let component: PosComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        PosComponent,
        ArticleGridComponent,
        CartComponent,
        ClientSearchComponent,
        PaymentModalComponent,
      ],
      imports: [
        HttpClientTestingModule,
        ReactiveFormsModule,
        RouterTestingModule,
        TranslateModule.forRoot(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PosComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose PosStep enum to template', () => {
    expect(component.PosStep).toBeDefined();
  });
});
