import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Reservation } from '../../../hebergement/models/reservation.model';
import { ReservationsService } from '../../../hebergement/services/reservations.service';
import { ServiceHotelier } from '../../models/service-hotelier.model';
import { LigneServiceService } from '../../services/ligne-service.service';
import { ServicesHoteliersService } from '../../services/services-hoteliers.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Composant Tour 51bis — Bridge ServiceHotelier ↔ LigneFacture (UI).
 *
 * Permet à un opérateur (RECEPTION/GERANT/ADMIN) d'ajouter une ligne service
 * (blanchisserie, navette, mini-bar consommé, etc.) à la facture associée
 * à une réservation, en choisissant :
 *   - la réservation en cours (parmi celles `en-cours`)
 *   - le service hôtelier (parmi les services actifs)
 *   - la quantité
 *   - un override de prix optionnel (sinon prix du service utilisé)
 *
 * Accessible via la route `/inventory/services/ajouter-a-facture`.
 */
@Component({
  selector: 'app-ajouter-service-facture',
  templateUrl: './ajouter-service-facture.component.html',
  standalone: false,
})
export class AjouterServiceFactureComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  reservations: Reservation[] = [];
  services: ServiceHotelier[] = [];
  selectedService: ServiceHotelier | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly router: Router,
    private readonly ligneServiceService: LigneServiceService,
    private readonly servicesService: ServicesHoteliersService,
    private readonly reservationsService: ReservationsService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadReferentiels();

    // Auto-fill libellé + prix unitaire si le service change
    this.form
      .get('serviceId')!
      .valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe((id: number | null) => {
        if (id == null) {
          this.selectedService = null;
          return;
        }
        const svc = this.services.find((s) => s.serviceId === Number(id));
        this.selectedService = svc ?? null;
        if (svc) {
          this.form.patchValue({
            libelle: svc.nomService,
            prixUnitaire: svc.prixUnitaire,
          });
        }
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
    this.state = 'submitting';
    const raw = this.form.getRawValue();
    this.ligneServiceService
      .addLigneService({
        reservationId: Number(raw.reservationId),
        serviceId: Number(raw.serviceId),
        quantite: Number(raw.quantite),
        prixUnitaire: raw.prixUnitaire != null ? Number(raw.prixUnitaire) : undefined,
        libelle: String(raw.libelle || '').trim() || undefined,
        datePrestation: raw.datePrestation || undefined,
      })
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
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('inventory.bridgeService.messages.success'),
            timer: 1800,
            showConfirmButton: false,
          });
          this.form.reset({
            reservationId: null,
            serviceId: null,
            quantite: 1,
            prixUnitaire: null,
            libelle: '',
            datePrestation: new Date().toISOString().substring(0, 10),
          });
          this.selectedService = null;
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('inventory.bridgeService.messages.error'),
          });
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/inventory/services-hoteliers']);
  }

  private buildForm(): FormGroup {
    const today = new Date().toISOString().substring(0, 10);
    return this.fb.group({
      reservationId: [null, [Validators.required]],
      serviceId: [null, [Validators.required]],
      quantite: [1, [Validators.required, Validators.min(0.01)]],
      prixUnitaire: [null, [Validators.min(0)]],
      libelle: [''],
      datePrestation: [today, [Validators.required]],
    });
  }

  private loadReferentiels(): void {
    forkJoin({
      services: this.servicesService.findActifs().pipe(catchError(() => of([] as ServiceHotelier[]))),
      reservations: this.reservationsService.enCours().pipe(catchError(() => of([] as Reservation[]))),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ services, reservations }) => {
        this.services = services;
        this.reservations = reservations;
        this.state = 'ready';
      });
  }

  reservationLabel(r: Reservation): string {
    const parts: string[] = [];
    if (r.numeroReservation) {
      parts.push(r.numeroReservation);
    } else if (r.reservationId != null) {
      parts.push(`#${r.reservationId}`);
    }
    if (r.dateArrivee && r.dateDepart) {
      parts.push(`(${r.dateArrivee} → ${r.dateDepart})`);
    }
    return parts.join(' ');
  }
}
