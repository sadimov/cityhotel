import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Societe, SocieteCreate } from '../../models/societe.model';
import { ClientsService } from '../../services/clients.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/** Formulaire création / édition d'une société client. */
@Component({
  selector: 'app-societe-form',
  templateUrl: './societe-form.component.html',
  standalone: false,
})
export class SocieteFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly clientsService: ClientsService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && idParam !== 'new') {
      const id = Number(idParam);
      if (!Number.isFinite(id)) {
        this.state = 'error';
        return;
      }
      this.editingId = id;
      this.loadExisting(id);
    } else {
      this.state = 'ready';
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isEditing(): boolean {
    return this.editingId !== null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const payload = this.form.getRawValue() as SocieteCreate;
    const obs$ = this.editingId
      ? this.clientsService.updateSociete(this.editingId, payload)
      : this.clientsService.createSociete(payload);

    obs$
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          if (this.state === 'submitting') {
            this.state = 'ready';
          }
        }),
      )
      .subscribe({
        next: () => {
          const key = this.editingId
            ? 'clients.societes.messages.updateSuccess'
            : 'clients.societes.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(key),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/clients/societes']);
        },
        error: (err) => {
          const key = err?.error?.error || 'clients.societes.messages.saveError';
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
          });
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/clients/societes']);
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      societeNom: ['', [Validators.required, Validators.maxLength(150)]],
      siret: ['', [Validators.maxLength(50)]],
      contactPrincipal: ['', [Validators.maxLength(100)]],
      telephone: ['', [Validators.maxLength(40)]],
      email: ['', [Validators.email, Validators.maxLength(150)]],
      adresse: ['', [Validators.maxLength(255)]],
      ville: ['', [Validators.maxLength(100)]],
      pays: ['', [Validators.maxLength(100)]],
    });
  }

  private loadExisting(id: number): void {
    this.clientsService
      .findSocieteById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (s) => {
          this.hydrate(s);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrate(s: Societe): void {
    this.form.patchValue({
      societeNom: s.societeNom ?? '',
      siret: s.siret ?? '',
      contactPrincipal: s.contactPrincipal ?? '',
      telephone: s.telephone ?? '',
      email: s.email ?? '',
      adresse: s.adresse ?? '',
      ville: s.ville ?? '',
      pays: s.pays ?? '',
    });
  }
}
