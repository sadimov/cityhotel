import { Component, EventEmitter, Output } from '@angular/core';

import { PosStore } from '../state/pos.store';

@Component({
  selector: 'app-pos-client-header',
  templateUrl: './client-header.component.html',
  styleUrls: ['./client-header.component.scss'],
  standalone: false,
})
export class ClientHeaderComponent {
  @Output() readonly openClientModal = new EventEmitter<void>();

  readonly selectedClient$ = this.store.selectedClient$;
  readonly selectedReservation$ = this.store.selectedReservation$;

  constructor(private readonly store: PosStore) {}

  onOpenClientModal(): void {
    this.openClientModal.emit();
  }

  onClearReservation(): void {
    this.store.selectReservation(null);
  }
}
