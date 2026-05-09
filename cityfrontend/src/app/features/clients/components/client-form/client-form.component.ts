import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  Client,
  ClientCreate,
  ClientUpdate,
} from '../../models/client.model';
import { ClientsService } from '../../services/clients.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire de création / édition d'un client.
 *
 * Mode déterminé via le paramètre de route `:id` :
 *  - `/clients/new` → création
 *  - `/clients/{id}` → édition (charge le client puis hydrate le form)
 */
@Component({
  selector: 'app-client-form',
  templateUrl: './client-form.component.html',
  styleUrls: ['./client-form.component.scss'],
  standalone: false,
})
export class ClientFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  /** ID du client édité, ou `null` en mode création. */
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

  /** Indique si on est en mode édition. */
  get isEditing(): boolean {
    return this.editingId !== null;
  }

  /** Soumission du formulaire. */
  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const payload = this.form.getRawValue() as ClientCreate;
    const obs$ = this.editingId
      ? this.clientsService.update(this.editingId, payload as ClientUpdate)
      : this.clientsService.create(payload);

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
          const successKey = this.editingId
            ? 'clients.messages.updateSuccess'
            : 'clients.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/clients']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('clients.messages.saveError'),
          });
        },
      });
  }

  /** Annulation — retour à la liste. */
  cancel(): void {
    this.router.navigate(['/clients']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      prenom: ['', [Validators.required, Validators.maxLength(100)]],
      nom: ['', [Validators.required, Validators.maxLength(100)]],
      email: ['', [Validators.email, Validators.maxLength(150)]],
      telephone: ['', [Validators.maxLength(40)]],
      adresse: ['', [Validators.maxLength(255)]],
      ville: ['', [Validators.maxLength(100)]],
      pays: ['', [Validators.maxLength(100)]],
      numeroIdentification: ['', [Validators.maxLength(50)]],
      dateNaissance: [''],
    });
  }

  private loadExisting(id: number): void {
    this.clientsService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (client) => {
          this.hydrateForm(client);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(client: Client): void {
    this.form.patchValue({
      prenom: client.prenom ?? '',
      nom: client.nom ?? '',
      email: client.email ?? '',
      telephone: client.telephone ?? '',
      adresse: client.adresse ?? '',
      ville: client.ville ?? '',
      pays: client.pays ?? '',
      numeroIdentification: client.numeroIdentification ?? '',
      dateNaissance: client.dateNaissance ?? '',
    });
  }
}
