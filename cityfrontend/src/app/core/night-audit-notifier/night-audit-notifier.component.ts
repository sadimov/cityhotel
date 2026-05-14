import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { AuthService } from '../../services/auth.service';
import { TranslationService } from '../../services/translation.service';
import {
  NIGHT_AUDIT_EVENT_TYPES,
  NightAuditEvent,
} from '../../features/hebergement/models/night-audit.model';
import { NightAuditNotificationsService } from '../services/night-audit-notifications.service';

/**
 * Composant — Écouteur SSE night audit (Tour 47 — refondu Tour 48).
 *
 * Rendu visuel : ce composant n'affiche aucun bloc inerte dans le flux du
 * layout. Les notifications passent par :
 *  - un toast SweetAlert2 top-end pour l'alerte T-3min (`ALERT_3MIN`)
 *  - une modale Bootstrap (déclarée dans son propre template) pour
 *    l'ouverture du démarrage (`OPEN_LAUNCH_MODAL`).
 *
 * Tour 48 — refonte modale :
 *  - bouton "Démarrer"  → ferme la modale + navigation vers
 *    `/hebergement/night-audit` (page de préparation où la réception valide
 *    les check-in/check-out avant de cliquer "Lancer la clôture").
 *  - bouton "Reporter"  → ferme la modale + memoize "snoozed" pour la
 *    journée (`localStorage["night_audit_snoozed_YYYY-MM-DD"] = "1"`).
 *    Tant que le flag est posé, l'event `night-audit-modal` du jour est
 *    ignoré côté front.
 *  - À l'init, purge les flags `night_audit_snoozed_*` antérieurs au jour
 *    courant (hygiène localStorage).
 *
 * Le composant est placé dans `main-layout.component.html` après le
 * `<app-header>` pour être actif sur toutes les routes authentifiées.
 *
 * Sécurité : le backend filtre déjà les events `night-audit-modal` sur les
 * rôles SUPERADMIN / ADMIN / NIGHTAUDIT, mais on double-check côté front pour
 * être robuste (cas d'un broadcast accidentel ou d'un changement de rôle).
 */
@Component({
  selector: 'app-night-audit-notifier',
  templateUrl: './night-audit-notifier.component.html',
  styleUrls: ['./night-audit-notifier.component.scss'],
  standalone: false,
})
export class NightAuditNotifierComponent
  implements OnInit, AfterViewInit, OnDestroy
{
  private readonly destroy$ = new Subject<void>();

  /** Rôles autorisés à lancer manuellement le night audit. */
  private static readonly ALLOWED_ROLES: readonly string[] = [
    'SUPERADMIN',
    'ADMIN',
    'NIGHTAUDIT',
  ];

  /** Préfixe des flags localStorage memoize "snooze night audit du jour". */
  private static readonly SNOOZE_KEY_PREFIX = 'night_audit_snoozed_';

  @ViewChild('launchModal', { static: false })
  private launchModalRef?: ElementRef<HTMLDivElement>;
  private launchModalInstance: BootstrapModal | null = null;

  /** Bind dans le template — date hôtelière affichée dans la modale. */
  readonly today: string = this.formatToday();

  constructor(
    private readonly notifications: NightAuditNotificationsService,
    private readonly auth: AuthService,
    private readonly i18n: TranslationService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.purgeStaleSnoozeFlags();
    this.notifications.connect();
    this.notifications.events$
      .pipe(takeUntil(this.destroy$))
      .subscribe((evt) => this.onEvent(evt));
  }

  ngAfterViewInit(): void {
    // Pré-instancie la modale Bootstrap pour qu'elle soit prête à afficher
    // immédiatement à la réception de l'event (évite un délai perceptible).
    const el = this.launchModalRef?.nativeElement;
    if (el) {
      this.launchModalInstance = this.createModalInstance(el);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    try {
      this.launchModalInstance?.dispose?.();
    } catch {
      // ignore
    }
    this.launchModalInstance = null;
    this.notifications.disconnect();
  }

  /* ────────────────────────────────────────────────────────────────────── */
  /*  Handlers template                                                       */
  /* ────────────────────────────────────────────────────────────────────── */

  /**
   * Clic "Démarrer" — ferme la modale et redirige vers la page de
   * préparation `/hebergement/night-audit`. C'est là que l'admin va valider
   * les check-in/check-out restants puis cliquer "Lancer la clôture".
   */
  start(): void {
    this.closeLaunchModal();
    this.router.navigate(['/hebergement/night-audit']);
  }

  /**
   * Clic "Reporter" — ferme la modale et memoize le snooze pour la journée
   * courante. Les prochains events `night-audit-modal` du jour seront
   * ignorés côté front.
   */
  snooze(): void {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(this.snoozeKey(), '1');
      }
    } catch {
      // ignore (private mode, quota, etc.)
    }
    this.closeLaunchModal();
    Swal.fire({
      toast: true,
      position: 'top-end',
      icon: 'info',
      title: this.i18n.translate(
        'hebergement.nightAudit.page.notifier.snoozed',
        'Night audit reporté pour aujourd’hui',
      ),
      timer: 3000,
      showConfirmButton: false,
      timerProgressBar: true,
    });
  }

  /** Clic "Fermer (croix)" — ferme la modale sans rien faire. */
  closeLaunchModal(): void {
    try {
      this.launchModalInstance?.hide?.();
    } catch {
      // ignore
    }
  }

  /* ────────────────────────────────────────────────────────────────────── */
  /*  Implémentation                                                          */
  /* ────────────────────────────────────────────────────────────────────── */

  private onEvent(evt: NightAuditEvent): void {
    if (
      evt.eventName === 'night-audit-alert' &&
      evt.type === NIGHT_AUDIT_EVENT_TYPES.ALERT_3MIN
    ) {
      this.showAlertToast(evt.message);
      return;
    }
    if (
      evt.eventName === 'night-audit-modal' &&
      evt.type === NIGHT_AUDIT_EVENT_TYPES.OPEN_LAUNCH_MODAL
    ) {
      if (!this.userCanLaunch()) {
        // Double check côté front — le backend filtre déjà mais on évite
        // d'ouvrir une modale invocable par un rôle non autorisé.
        return;
      }
      if (this.isSnoozedToday()) {
        // L'utilisateur a déjà choisi "Reporter" pour aujourd'hui — on
        // ignore l'event sans afficher la modale.
        return;
      }
      this.openLaunchModal();
    }
  }

  private userCanLaunch(): boolean {
    return this.auth.hasAnyRole([...NightAuditNotifierComponent.ALLOWED_ROLES]);
  }

  private showAlertToast(serverMessage: string): void {
    const i18nMessage = this.i18n.translate(
      'nightAudit.alert.message',
      serverMessage || 'Night audit imminent.',
    );
    const title = this.i18n.translate(
      'nightAudit.alert.title',
      'Night audit imminent',
    );
    Swal.fire({
      toast: true,
      position: 'top-end',
      icon: 'warning',
      title,
      text: i18nMessage,
      timer: 10_000,
      showConfirmButton: false,
      timerProgressBar: true,
    });
  }

  private openLaunchModal(): void {
    const el = this.launchModalRef?.nativeElement;
    if (!el) return;
    if (!this.launchModalInstance) {
      this.launchModalInstance = this.createModalInstance(el);
    }
    try {
      this.launchModalInstance?.show?.();
    } catch {
      // ignore — la modale est déjà visible ou bootstrap n'est pas chargé.
    }
  }

  private createModalInstance(el: HTMLElement): BootstrapModal | null {
    const bs = (
      window as unknown as {
        bootstrap?: { Modal?: BootstrapModalCtor };
      }
    ).bootstrap;
    if (!bs?.Modal) return null;
    return new bs.Modal(el, { backdrop: 'static', keyboard: false });
  }

  // ── Memoize "snooze night audit du jour" ─────────────────────────────────

  /** Clé localStorage memoize pour la date hôtelière courante. */
  private snoozeKey(): string {
    return `${NightAuditNotifierComponent.SNOOZE_KEY_PREFIX}${this.today}`;
  }

  /** Vrai si l'utilisateur a déjà cliqué "Reporter" aujourd'hui. */
  private isSnoozedToday(): boolean {
    try {
      if (typeof localStorage === 'undefined') return false;
      return localStorage.getItem(this.snoozeKey()) === '1';
    } catch {
      return false;
    }
  }

  /**
   * Purge les flags `night_audit_snoozed_*` qui ne correspondent pas à la
   * date hôtelière courante. Évite l'accumulation de clés mortes en
   * localStorage (hygiène).
   */
  private purgeStaleSnoozeFlags(): void {
    try {
      if (typeof localStorage === 'undefined') return;
      const keep = this.snoozeKey();
      const toRemove: string[] = [];
      for (let i = 0; i < localStorage.length; i++) {
        const k = localStorage.key(i);
        if (
          k &&
          k.startsWith(NightAuditNotifierComponent.SNOOZE_KEY_PREFIX) &&
          k !== keep
        ) {
          toRemove.push(k);
        }
      }
      for (const k of toRemove) {
        localStorage.removeItem(k);
      }
    } catch {
      // ignore
    }
  }

  /** Format `YYYY-MM-DD` de la date hôtelière courante (timezone client). */
  private formatToday(): string {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}

// ── Types Bootstrap (typage minimal — bootstrap.bundle est chargé global) ──

interface BootstrapModal {
  show?: () => void;
  hide?: () => void;
  dispose?: () => void;
}

interface BootstrapModalCtor {
  new (
    el: HTMLElement,
    opts?: { backdrop?: boolean | 'static'; keyboard?: boolean },
  ): BootstrapModal;
}
