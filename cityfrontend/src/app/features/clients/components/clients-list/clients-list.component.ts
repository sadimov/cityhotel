import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Client, PageRequest, PageResponse } from '../../models/client.model';
import { ClientsService } from '../../services/clients.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste des clients avec pagination + recherche serveur.
 *
 * Le composant ne dépend pas de DataTables.net pour rester portable et
 * facilement testable ; un wrapper `<app-data-table>` pourra être branché
 * plus tard via le `SharedModule` (cf. cityfrontend/CLAUDE.md §4.2).
 */
@Component({
  selector: 'app-clients-list',
  templateUrl: './clients-list.component.html',
  styleUrls: ['./clients-list.component.scss'],
  standalone: false,
})
export class ClientsListComponent implements OnInit, OnDestroy {
  /** État courant de la vue (loading / ready / empty / error). */
  state: ListState = 'loading';

  /** Page courante de résultats serveur. */
  page: PageResponse<Client> | null = null;

  /** Requête de pagination courante. */
  request: PageRequest = { page: 0, size: 10, sortBy: 'nom', sortDir: 'asc' };

  /** Saisie de recherche (ngModel via FormsModule). */
  searchTerm = '';

  /** Indique si une suppression est en cours. */
  deleting = false;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly clientsService: ClientsService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Recharge la page courante depuis le serveur.
   */
  load(): void {
    this.state = 'loading';
    this.clientsService
      .page(this.request)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((p) => {
        if (!p) {
          // état déjà passé à 'error'
          return;
        }
        this.page = p;
        this.state = p.numberOfElements === 0 ? 'empty' : 'ready';
      });
  }

  /**
   * Déclenche une recherche après un debounce léger (300 ms) pour éviter
   * de spammer l'API à chaque frappe.
   */
  onSearchChange(value: string): void {
    this.searchTerm = value;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => {
      this.request = { ...this.request, page: 0, recherche: value };
      this.load();
    }, 300);
  }

  /** Change de page (0-based). */
  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) {
      return;
    }
    this.request = { ...this.request, page };
    this.load();
  }

  /** Navigation vers le formulaire de création. */
  createNew(): void {
    this.router.navigate(['/clients', 'new']);
  }

  /** Navigation vers le formulaire d'édition. */
  edit(client: Client): void {
    if (client.clientId == null) {
      return;
    }
    this.router.navigate(['/clients', client.clientId]);
  }

  /** Suppression confirmée par SweetAlert2. */
  remove(client: Client): void {
    if (client.clientId == null) {
      return;
    }
    const id = client.clientId;
    Swal.fire({
      title: this.i18n.translate('clients.messages.deleteConfirm'),
      text: client.nomComplet ?? `${client.prenom} ${client.nom}`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('clients.actions.delete'),
      cancelButtonText: this.i18n.translate('clients.actions.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.deleting = true;
      this.clientsService
        .delete(id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.deleting = false;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('clients.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('clients.messages.deleteError'),
            });
          },
        });
    });
  }

  /** Helper template — borne la pagination affichée. */
  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  /** Helper template — label "Aucun" sécurisé. */
  displayName(client: Client): string {
    return client.nomComplet ?? `${client.prenom} ${client.nom}`;
  }
}
