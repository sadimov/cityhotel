import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';

import { Personnel } from '../../models/personnel.model';

/**
 * Modal maison d'assignation d'une tâche à un personnel.
 *
 * Composant **présentationnel** : il ne sait rien du HTTP.
 *  - Inputs : `tacheId`, `currentPersonnelId`, `personnels` (liste
 *    déjà filtrée actifs/disponibles par le parent), `submitting`.
 *  - Output : `(assigned)` émet `{ tacheId, personnelId }` ; le parent
 *    appelle ensuite `tachesService.assigner(...)` (cf. `tache-detail`).
 *
 * Pattern modal aligné sur `restaurant/pos/payment-modal` (Tour 24) :
 *  - overlay `.modal-backdrop` `position: fixed inset: 0`
 *  - `role=dialog`, `aria-modal=true`
 *  - z-index 1080 (au-dessus du backdrop Bootstrap natif éventuel)
 *  - bouton Fermer + bouton Annuler + bouton Valider
 *
 * Le composant est invocable depuis `tache-detail/` (où il est
 * actuellement utilisé) et peut l'être aussi depuis `taches-list/`
 * lorsqu'on voudra factoriser le SweetAlert actuel — ce refactor
 * n'est pas dans le scope Tour 28 pour ne pas régresser.
 */
@Component({
  selector: 'app-assignation-personnel',
  templateUrl: './assignation-personnel.component.html',
  styleUrls: ['./assignation-personnel.component.scss'],
  standalone: false,
})
export class AssignationPersonnelComponent implements OnChanges {
  @Input() open = false;
  @Input() tacheId: number | null = null;
  @Input() currentPersonnelId: number | null = null;
  @Input() personnels: Personnel[] = [];
  @Input() submitting = false;

  @Output() readonly close = new EventEmitter<void>();
  @Output() readonly assigned = new EventEmitter<{
    tacheId: number;
    personnelId: number;
  }>();

  /** Personnel actuellement sélectionné dans la modal. */
  selectedPersonnelId: number | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      // Ré-initialise la sélection au courant à chaque ouverture.
      this.selectedPersonnelId = this.currentPersonnelId;
    }
    if (changes['currentPersonnelId'] && !changes['currentPersonnelId'].firstChange) {
      this.selectedPersonnelId = this.currentPersonnelId;
    }
  }

  onSelect(personnelId: number): void {
    this.selectedPersonnelId = personnelId;
  }

  /**
   * Pour le `change` du select natif — `event.target.value` est une string.
   * On accepte aussi `''` pour permettre la sélection « aucun ».
   */
  onSelectChange(value: string): void {
    if (value === '') {
      this.selectedPersonnelId = null;
      return;
    }
    const id = Number(value);
    if (Number.isFinite(id)) {
      this.selectedPersonnelId = id;
    }
  }

  onCancel(): void {
    this.close.emit();
  }

  onConfirm(): void {
    if (this.tacheId == null || this.selectedPersonnelId == null) {
      return;
    }
    this.assigned.emit({
      tacheId: this.tacheId,
      personnelId: this.selectedPersonnelId,
    });
  }

  /** Helper template — désactivation du bouton Valider. */
  get canSubmit(): boolean {
    return (
      this.tacheId != null &&
      this.selectedPersonnelId != null &&
      !this.submitting
    );
  }

  /** Pour la mise en avant visuelle du personnel actuellement assigné. */
  isCurrent(personnelId: number | undefined): boolean {
    return personnelId != null && personnelId === this.currentPersonnelId;
  }
}
