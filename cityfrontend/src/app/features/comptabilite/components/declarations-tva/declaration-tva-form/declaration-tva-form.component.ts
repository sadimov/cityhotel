import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { TvaService } from '../../../services/tva.service';

/**
 * Formulaire de création d'une déclaration TVA (saisie période, B7).
 *
 * Le service back calcule les agrégats et persiste en BROUILLON.
 * Validation de la période côté front : dateFin >= dateDebut.
 */
@Component({
  selector: 'app-declaration-tva-form',
  templateUrl: './declaration-tva-form.component.html',
  standalone: false,
})
export class DeclarationTvaFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  submitting = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly service: TvaService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      dateDebut: ['', Validators.required],
      dateFin: ['', Validators.required],
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { dateDebut, dateFin } = this.form.value;
    if (dateFin < dateDebut) {
      Swal.fire({
        icon: 'warning',
        title: this.i18n.translate('error.declaration.periodeInvalid'),
      });
      return;
    }

    this.submitting = true;
    this.service
      .createDeclaration({ dateDebut, dateFin })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.submitting = false)),
      )
      .subscribe({
        next: () => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('comptabilite.declarationTva.messages.createSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/comptabilite/tva/declarations']);
        },
        error: (err) => {
          const key = err?.error?.message || 'error.declaration.createFailed';
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
          });
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/comptabilite/tva/declarations']);
  }
}
