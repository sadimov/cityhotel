import { Component, OnInit, ViewChild, ChangeDetectionStrategy, ChangeDetectorRef, ViewContainerRef, AfterViewInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { MatMenuTrigger } from '@angular/material/menu';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { ReservationClient, Facture, GestionFactureReservation, Reservation, User, Client, TypeIdentification, Nationalite, Societe, Chambre, TypeChambre, UpdatedReservation } from '@app/_models';
import { AuthenticationService, ChambreService, ClientService, CompteService, FactureService, GestionFactureReservationService, HotelService, LigneFactureService, NationaliteService, NuiteeService, OperationCompteService, ReservationClientService, ReservationService, SocieteClientService, SocieteReservationService, SocieteService, TypeChambreService, TypeIdentificationService } from '@app/_services';
import { addDays, differenceInCalendarDays } from 'date-fns';  // (Optional: date utility for date calculations)
import { BehaviorSubject } from 'rxjs';
import { first } from 'rxjs/operators';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import Swal from 'sweetalert2';
import e from 'express';
import { UserHelperService } from '@app/shared/services/user-helper.service';
// Import or define interfaces for Room and Reservation
// (These could be in separate files in a real project)
interface Room {
  id: number;
  number: string;
  type: string;
}


// Placeholder for reservation service that would handle API calls and real-time data

declare var $: any;
@Component({
  selector: 'app-calendar',
  templateUrl: './calendar.component.html',
  styleUrls: ['./calendar.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CalendarComponent implements OnInit {
  @ViewChild('contextMenuTemplate') contextMenuTemplate!: any;

  private overlayRef!: OverlayRef;
  showContextMenu = false;
  contextMenuPosition = { x: 0, y: 0 };

  rooms: Chambre[] = [];                      // List of rooms (populated on init)
  roomTypes: string[] = [];                // Distinct room types for grouping
  roomsByType: { [type: string]: Chambre[] } = {};  // Rooms grouped by type for easier template rendering
  reservationsClient: ReservationClient[] = [];        // List of reservations
  days: Date[] = [];                       // Array of Date objects for each day column in current view
  viewStart!: Date;                         // Start date of currently displayed range
  viewEnd!: Date;                           // End date of currently displayed range
  today: Date = new Date();                // Today's date for comparisons (midnight to avoid time mismatches)

  // Variables for drag-to-select reservation range
  dragging: boolean = false;
  dragStartRoom: Chambre | null = null;
  dragStartDate: Date | null = null;
  dragEndDate: Date | null = null;
  selectedCells = new Set<string>();

  user?: User | null;

  // Context menu trigger and selection
  selectedReservation!: Reservation;  // Reservation currently targeted by context menu
  selectedReservationClient: ReservationClient | null = null;  // Reservation currently targeted by context menu

  clients!: Client[];
  typeIdentifications!: TypeIdentification[];
  nationalites!: Nationalite[];
  societes!: Societe[];
  loading = true;

  addedChambres: Chambre[] = [];
  montantReservation: number = 0;
  periodeReservation: number = 0;
  reductionReservation: number = 0;
  addedClients: Client[] = [];
  societe!: Societe;
  isCreatingReservation = false;

  currentConsultReservCltChmb!: ReservationClient;
  factureConsult!: Facture;
  consultLoading = true;
  sousTotalSumC: number = 0;
  totalAffectationSum: number = 0;
  montantRestantSum: number = 0;
  gestionFactureReservations: GestionFactureReservation[] = [];

  inModifReservCltChmb!: ReservationClient | undefined;
  
  modifyReservationForm!: FormGroup;
  newModClientId!: number;
  newModChambreId!: number;
  newModSocieteId!: number;

  constructor(
    public userHelper: UserHelperService,
    private dialog: MatDialog,
    private cdRef: ChangeDetectorRef,
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    private fb: FormBuilder,
    private reservationService: ReservationService,
    private reservationClientService: ReservationClientService,
    private clientService: ClientService,
    private compteService: CompteService,
    private operationCompteService: OperationCompteService,
    private chambreService: ChambreService,
    private typeChambreService: TypeChambreService,
    private typeIdentificatinService: TypeIdentificationService,
    private nationaliteService: NationaliteService,
    private societeService: SocieteService,
    private router: Router,
    private factureService: FactureService,
    private nuiteeService: NuiteeService,
    private ligneFactureService: LigneFactureService,
    private hotelService: HotelService,
    private societeClientService: SocieteClientService,
    private societeReservationService: SocieteReservationService,
    private gestionFactureReservationService: GestionFactureReservationService,
                    
    private authenticationService: AuthenticationService
  ) {
    //this.authenticationService.user.subscribe(x => this.user = x);
    this.user = this.userHelper.currentUser;
  }

  ngOnInit(): void {
    //$('#sidebarMenu').css('margin-left', '-240px')
    //$('#main-container').css({'margin-top': '58px', 'padding-left': '45px'})
    //$('#ptContainer').removeClass('container');
    // Initialize the default view to the current month (first day to last day of month)
    
    const now = new Date();
    this.viewStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()-2); // 14th day of current month
    this.viewEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate()+27); // last day of current month
    this.generateDaysArray();

    // Load or set room data (in real scenario, fetch from API)
    /** 
     * RECUPERATION DES DONNEES ET PREPARATION DE L'AFFICHAGE 
     */
    this.clientService.getAllByHotel(this.user?.hotel?.hotelId).pipe(first()).subscribe(clients => {        
      this.clients = clients;
      console.log(this.clients);
      this.chambreService.getAllByHotel(this.user?.hotel?.hotelId).pipe(first()).subscribe(chambres => {
          this.rooms = chambres
          console.log(this.rooms);
          this.typeChambreService.getAll().pipe(first()).subscribe(typesChambre => {
              //this.roomTypes = typesChambre;
              console.log(typesChambre);
              this.reservationClientService.getAllByHotel(this.user?.hotel?.hotelId).pipe(first()).subscribe(reservsClt => {                    
                  this.reservationsClient = reservsClt;
                  console.log(this.reservationsClient);  
                  this.loading = false; 
                  this.groupRoomsByType();

                  
                  console.log(this.roomTypes);
                  this.cdRef.detectChanges();
                  let that = this;
                  $('input[name="daterange"]').daterangepicker({
                    opens: 'left'
                  }, function(start: any, end: any, label: any) {
                      that.changeDateRange(start, end);
                      //console.log("A new date selection was made: " + start.format('YYYY-MM-DD') + ' to ' + end.format('YYYY-MM-DD'));
                  });
                  console.log("this.loading: " + this.loading);                                     
              });
          })           
      });
      this.typeIdentificatinService.getAll().pipe(first()).subscribe(types => {
          this.typeIdentifications = types;
      });
      this.nationaliteService.getAll().pipe(first()).subscribe(nationalites => {
          this.nationalites = nationalites;
      });
      this.societeService.getAll().pipe(first()).subscribe(societes => {
          this.societes = societes;    
      });
  });      
   /**
   * FIN DE RECUPERATION DES DONNEES
   */
    document.addEventListener('click', () => {
      this.showContextMenu = false;
    });
  }

  
  

  
  loadData() {
  this.loading = true;
  this.clientService.getAllByHotel(this.user?.hotel?.hotelId).pipe(first()).subscribe(clients => {        
    this.clients = clients;
    
    this.chambreService.getAllByHotel(this.user?.hotel?.hotelId).pipe(first()).subscribe(chambres => {
      this.rooms = chambres;
      
      this.typeChambreService.getAll().pipe(first()).subscribe(typesChambre => {
        //this.roomTypes = typesChambre;

        this.reservationClientService.getAllByHotel(this.user?.hotel?.hotelId).pipe(first()).subscribe(reservsClt => {
          this.reservationsClient = reservsClt;

          this.groupRoomsByType();
          this.loading = false;
          this.cdRef.detectChanges();

          console.log("Données chargées");
        });
      });
    });

    this.typeIdentificatinService.getAll().pipe(first()).subscribe(types => {
      this.typeIdentifications = types;
    });
    this.nationaliteService.getAll().pipe(first()).subscribe(nationalites => {
      this.nationalites = nationalites;
    });
    this.societeService.getAll().pipe(first()).subscribe(societes => {
      this.societes = societes;
    });
  });
}

  /** Generate the array of days between viewStart and viewEnd inclusive */
  private generateDaysArray(): void {
    this.days = [];
    let current = new Date(this.viewStart.getTime());
    while (current <= this.viewEnd) {
      this.days.push(new Date(current));
      current.setDate(current.getDate() + 1);
    }
  }

  /** Group rooms by their type for rendering grouped rows */
  private groupRoomsByType(): void {
    this.roomTypes = [];
    this.roomsByType = {};
    for (const room of this.rooms) {
      if (!this.roomsByType[room.chambreType.typeChambreLibele]) {
        this.roomsByType[room.chambreType.typeChambreLibele] = [];
        this.roomTypes.push(room.chambreType.typeChambreLibele);
      }
      this.roomsByType[room.chambreType.typeChambreLibele].push(room);
    }
  }

  /** Helper to check if a given date is a weekend (Saturday/Sunday) */
  isWeekend(date: Date): boolean {
    const day = date.getDay();
    return day === 0 || day === 6; // 0 = Sunday, 6 = Saturday
  }

  /** Helper to check if a date is in the past (before today) */
  isPast(date: Date): boolean {
    // Compare date at midnight to today at midnight
    const todayMidnight = new Date(this.today.getFullYear(), this.today.getMonth(), this.today.getDate());
    return date < todayMidnight;
  }

  /** Helper to check if a given date is today */
  isToday(date: Date): boolean {
    return date.toDateString() === this.today.toDateString();
  }

  /** Find a reservation that starts on a given date for a specific room (returns null if none) */
  getReservationStarting(roomId: number, date: Date): ReservationClient | null {
    return this.reservationsClient.find(reservationClient =>
      reservationClient.chambre && 
      reservationClient.chambre.id === roomId &&
      reservationClient.reservation &&
      this.isSameDay(new Date(reservationClient.reservation.reservationDateDebut), date)
    ) || null;
  }

  /** Calculate the length in days of a reservation (inclusive of start and end dates) */
  getReservationSpanDays(res: Reservation): number {
    const resDebut = new Date(res.reservationDateDebut);
    const resFin = new Date(res.reservationDateFin);
    const start = new Date(resDebut.getFullYear(), resDebut.getMonth(), resDebut.getDate());
    const end = new Date(resFin.getFullYear(), resFin.getMonth(), resFin.getDate());
    const diffTime = end.getTime() - start.getTime();
    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24)) + 1;
    return diffDays;
  }

  /** Utility to compare two dates by year, month, and day (ignoring time) */
  private isSameDay(d1: Date, d2: Date): boolean {
    return d1.getDate() === d2.getDate() &&
           d1.getMonth() === d2.getMonth() &&
           d1.getFullYear() === d2.getFullYear();
  }

  /** Start dragging to select a new reservation range */
  startDrag(room: Chambre, date: Date): void {
    if (this.isPast(date)) return;  // do not allow starting on past dates
    // Ensure the cell is free (not in an existing reservation)
    const res = this.getReservationStarting(room.id, date) || this.isDateWithinReservation(room.id, date);
    if (res) return;  // if any reservation covers this date, skip (no double-booking)
    this.dragging = true;
    this.dragStartRoom = room;
    this.dragStartDate = date;
    this.dragEndDate = date;
    const cellId = this.getCellId(room.id, date);
    this.selectedCells.add(cellId);
  }

  /** Continue dragging over cells (mouse entered a new cell while dragging) */
  onDragOver(room: Chambre, date: Date): void {
    if (!this.dragging || !this.dragStartRoom || !this.dragStartDate) return;
    // Only allow drag selection within the same room row
    if (room.id !== this.dragStartRoom.id) return;
    if (this.isPast(date)) return;
    // Update the end of the selection range as the latest hovered date (ensure it's not before start)
    if (date >= this.dragStartDate) {
      this.dragEndDate = date;
    } else {
      this.dragEndDate = this.dragStartDate;
      this.dragStartDate = date;
    }
    const cellId = this.getCellId(room.id, date);
    this.selectedCells.add(cellId);
  }

  /** Finish dragging to create a reservation over the selected range */
  endDrag(room: Chambre, date: Date): void {
    if (!this.dragging || !this.dragStartRoom || !this.dragStartDate || !this.dragEndDate) {
      this.resetDrag();
      return;
    }
    this.dragging = false;
    // Only finalize if the mouseup is on the same room row
    if (room.id === this.dragStartRoom.id) {
      const start = this.dragStartDate <= this.dragEndDate ? this.dragStartDate : this.dragEndDate;
      const end = this.dragEndDate >= this.dragStartDate ? this.dragEndDate : this.dragStartDate;
      // Open the reservation creation dialog for the selected room and date range
      this.openCreateReservationDialog(room, start, end);
    }
    const cellId = this.getCellId(room.id, date);
    this.selectedCells.add(cellId);
    this.resetDrag();
  }

  /** Reset drag selection variables */
  private resetDrag(): void {
    this.dragging = false;
    this.dragStartRoom = null;
    this.dragStartDate = null;
    this.dragEndDate = null;
  }

  /** Check if a given date (within current view) falls inside an existing reservation for the room (not just start) */
  private isDateWithinReservation(roomId: number, date: Date): boolean {
    const reservation = this.reservationsClient.find(res =>
      res.chambreId === roomId &&
      res.reservation &&
      res.reservation.reservationDateDebut <= date &&
      res.reservation.reservationDateFin >= date
    );
    return !!reservation;
  }

  /** Open a dialog to create a new reservation, pre-filled with given room and date range */
  openCreateReservationDialog(room: Chambre, start: Date, end: Date): void {
    let that = this;
    // Open Angular Material Dialog (assume NewReservationDialogComponent is implemented separately)
    /*const dialogRef = this.dialog.open(NewReservationDialogComponent, {
      width: '400px',
      data: { room, startDate: start, endDate: end }
    });*/
    // After dialog closes, if a reservation is created, save it (e.g., via service)
    /*dialogRef.afterClosed().subscribe(result => {
      if (result) {
        // Call service to create the reservation (which will trigger update to reservations$ BehaviorSubject)
        this.reservationService.createReservation(result).subscribe();
      }
    });*/

    $( "#reservModal" ).on('shown.bs.modal', function(e:any){
      that.addChambre(room);
      that.calculateDuration();
    });   
    
    $('#currentReservationStartDate').text(start);
    $('#currentReservationEndDate').text(end);
    $('#resdd').text(start.toLocaleDateString('fr-FR'));
		$('#resdf').text(end.toLocaleDateString('fr-FR'));   
    $('#currentLocationId').text(room.id);

    $('#reservModal').modal('show');

    console.log('Create reservation for room', room, 'from', start, 'to', end);

    this.cdRef.detectChanges();
  }

  /** Handle clicking an existing reservation to view or edit details */
  openReservationDetails(reservationClient: ReservationClient): void {
    /*this.dialog.open(ReservationDetailsDialogComponent, {
      width: '500px',
      data: { reservation }
    });*/
    //$('#consultReservId').text(reservationClient.reservatinClientId);
    //this.cdRef.detectChanges();
    $( "#consultReservModal" ).modal('show');
    this.consultReservation(reservationClient.reservatinClientId);
    console.log('View reservation details:', reservationClient.reservation);
  }

  /** Open the context menu on right-click of a reservation */
  /** Ouvrir le menu contextuel à la position du curseur */
  openContextMenu(event: MouseEvent, reservationClt: ReservationClient): void {
    event.preventDefault();
  
    if (reservationClt.reservation?.reservationStatus === 3) return;
  
    this.selectedReservationClient = reservationClt;
    this.selectedReservation = reservationClt.reservation!;
  
    const x = event.clientX;
    const y = event.clientY;
  
    // Décaler le rendu à l'extérieur du cycle de l'événement
    setTimeout(() => {
      this.contextMenuPosition = { x, y };
      this.showContextMenu = true;
      this.cdRef.detectChanges();
    }, 0);
  }
  
  
  

  simulateFakeRightClick(): void {
    // Attendre que le DOM soit complètement rendu
    setTimeout(() => {
      const reservationCell = document.querySelector('.reservation-cell') as HTMLElement;
      const reservationClt = this.reservationsClient.find(r =>
        r.reservation?.reservationStatus !== 3 &&
        r.chambreId && r.reservation?.reservationDateDebut
      );
  
      if (reservationCell && reservationClt) {
        const rect = reservationCell.getBoundingClientRect();
        const clientX = rect.left + 5;
        const clientY = rect.top + 5;
  
        const fakeEvent = new MouseEvent('contextmenu', {
          bubbles: true,
          cancelable: true,
          clientX,
          clientY,
          view: window
        });
  
        // Important : utiliser requestAnimationFrame pour forcer le timing exact
        requestAnimationFrame(() => {
          this.openContextMenu(fakeEvent, reservationClt);
  
          // Refermer immédiatement après affichage (10 ms)
          setTimeout(() => {
            if (this.overlayRef) {
              this.overlayRef.dispose();
            }
          }, 10);
        });
      } else {
        console.warn('Aucune cellule de réservation visible ou réservation introuvable');
      }
    }, 150); // Délai plus large pour s’assurer que le DOM est prêt
  }
  
  
  

  // Context menu action handlers (these would include actual logic in a real app):
  async checkIn() {
    this.overlayRef?.dispose();
    if (!this.selectedReservation) return;
    // Example: update reservation status to "checked in" (status code might be different in real scenario)
    //****this.reservationService.updateReservationStatus(this.selectedReservation.id, 2 /* e.g., 2 for confirmed/check-in */).subscribe();
    if(confirm("Check In Réservation?")){
      if(this.selectedReservation) await this.reservationService.checkInReservation(this.selectedReservation.reservationId).subscribe();
      this.router.navigateByUrl('reception');
    }
  }
  checkOut(): void {
    if (!this.selectedReservation) return;
    //****this.reservationService.updateReservationStatus(this.selectedReservation.id, 4 /* e.g., 4 for checked-out */).subscribe();
    this.goToPayments();
  }
  modifyReservation(): void {
    
    if (!this.selectedReservation) return;
    // Open the same create dialog but in edit mode (pass existing reservation data)
    //***this.dialog.open(EditReservationDialogComponent, { data: { reservation: this.selectedReservation } });
    this.openModifyReservationModal(this.selectedReservationClient!);
    this.overlayRef?.dispose();
    console.log('Edit reservation:', this.selectedReservation);
    
  }
  goToPayments(): void {
    if (!this.selectedReservation) return;
    // Navigate to payments or open payment modal (implementation depends on app structure)
    //****this.dialog.open(PaymentDialogComponent, { data: { reservation: this.selectedReservation } });
    //go to route
    this.router.navigate(['/clients/pay-client', this.selectedReservationClient?.reservatinClientId]);
    console.log('Navigate to payments for reservation:', this.selectedReservation);
  }

  /** Handle changes in the displayed date range (from date picker) */
  onDateRangeChange(event: any): void {
    const start = event.value?.start;
    const end = event.value?.end;

    if (start && end) return;
    this.viewStart = start;
    this.viewEnd = end;
    this.generateDaysArray();
    // Reload reservations for the new range
    //*****this.reservationService.loadReservations(this.viewStart, this.viewEnd);
  }

  /** Helper function to check if a reservation's start date is near (tomorrow or today) */
isStartDateNear(startDate: Date): boolean {
  const today = new Date();
  today.setHours(0, 0, 0, 0); // Normalize to midnight for comparison

  const tomorrow = new Date();
  tomorrow.setDate(today.getDate() + 1);
  tomorrow.setHours(0, 0, 0, 0);

  return new Date(startDate).getTime() === today.getTime() || new Date(startDate).getTime() === tomorrow.getTime();
}

/** Helper function to check if a reservation's end date is near (today or tomorrow) */
isEndDateNear(endDate: Date): boolean {
  const today = new Date();
  today.setHours(0, 0, 0, 0); // Normalize to midnight for comparison

  const tomorrow = new Date();
  tomorrow.setDate(today.getDate() + 1);
  tomorrow.setHours(0, 0, 0, 0);

  return new Date(endDate).getTime() === today.getTime() || new Date(endDate).getTime() === tomorrow.getTime();
}

getFormattedDay(date: Date): string {
  const formatted = date.toLocaleDateString('fr-FR', { weekday: 'short' });
  return formatted.charAt(0).toUpperCase() + formatted.slice(1).replace('.', '');
}

changeDateRange(start: string, end: string){
  console.log("Date range a été changé avec: DEB = " + start + " et FIN = " + end);
  this.viewStart = new Date(start);
  console.log(new Date(start))
  this.viewEnd   = new Date(end);
  console.log(new Date(end));

  this.generateDaysArray();
  this.groupRoomsByType();
  this.cdRef.detectChanges();
}

getRenderedDays(roomId: number): Date[] {
  const renderedDays: Date[] = [];
  let i = 0;

  while (i < this.days.length) {
    const day = this.days[i];
    const reservation = this.getReservationStarting(roomId, day);

    if (reservation) {
      // Ajouter uniquement le premier jour du colspan
      renderedDays.push(day);
      // Sauter le nombre de jours correspondant au colspan
      i += this.getReservationSpanDays(reservation.reservation!) - 1;
    } else {
      renderedDays.push(day);
    }

    i++; // Incrémenter l'index
  }

  return renderedDays;
}

/** Supprimer manuellement la sélection */
clearSelection(): void {
  this.selectedCells.clear();
}

/** Générer un identifiant unique basé sur la chambre et le jour */
private getCellId(roomId: number, day: Date): string {
  return `${roomId}-${day.toISOString().split('T')[0]}`;
}

/** Vérifier si une cellule est sélectionnée */
isCellSelected(roomId: number, day: Date): boolean {
  const cellId = this.getCellId(roomId, day);
  return this.selectedCells.has(cellId);
}

/*##################################################################################################################*/
/*---------------------------------------------------METIER DE RESERVATION------------------------------------------*/
/*-----*CREATION*----*CONSULTATION*----*MODIFICATION*----*CHECKIN*----*CHECKOUT*-----*PAIEMENTS*----*ANNULATION*----*/
/*##################################################################################################################*/

addChambre(chambre: Chambre) {
  //console.log(chambre);

  if(this.addedChambres.length >= 1){
    this.rooms.push(this.addedChambres[0]);
  }
  this.addedChambres = [];

  //if(this.addedChambres.length >= 1) delete this.addedChambres[0];
  this.addedChambres.push(chambre);
  this.calculateReservationTotal();
  this.rooms = this.rooms.filter((chm) => chm.id !== chambre.id);      
}

calculateReservationTotal(){
  
  let totalChambres = 0;
  if(this.addedChambres.length > 0 && this.periodeReservation > 0){
    this.addedChambres.forEach(function (chambre) {
      chambre.chambreType.chambrePrix.forEach(function (price) {
        if(price.chambrePrixDateFin === null) {
          totalChambres = totalChambres + price.chambrePrix;
        }
      }); 
    });
    this.montantReservation = (totalChambres * this.periodeReservation) - ((this.reductionReservation * (totalChambres * this.periodeReservation))/100); 
  }else{
    this.montantReservation = 0;
  }

  this.cdRef.detectChanges();
  console.log("calculateReservationTotal(): ");
  console.log("this.addedChambres: ");
  console.log(this.addedChambres);
}


calculateDuration(){
  console.log("calculateDuration()")
  let ddebut: string      =               $('#currentReservationStartDate').text() as string;
  let dfin: string        =          $('#currentReservationEndDate').text() as string;
  
  console.log("ddebut: " + ddebut)
  console.log("dfin: " + dfin)

  if(ddebut !== "" && dfin !== ""){
    let dateDebut: Date = new Date(ddebut);
    let dateFin:   Date = new Date(dfin);
    let periode = (dateFin.getTime() - dateDebut.getTime())/(24 * 60 * 60 * 1000)
    
    this.periodeReservation = periode + 1;
    console.log("PERIODE: " + this.periodeReservation)
    this.calculateReservationTotal();
  }

  this.cdRef.detectChanges();
}

addClient(client: Client) {
    console.log("addClient");
    if(this.addedClients.length >= 1){
      this.clients.push(this.addedClients[0]);
    }
    this.addedClients = [];

    client.hotelId = this.user?.hotel?.hotelId;
    this.addedClients.push(client);
    this.clients = this.clients.filter((clt) => clt.id !== client.id);

    $('#prenom').val(client.prenom);
    $('#identifiant').val(client.identification);
    $('#tel').val(client.tel);
    $('#address').val(client.adress);
    console.log("this.addedClients: ");
    console.log(this.addedClients);

}

async createClient(){
    const Toast = Swal.mixin({
      toast: true,
      position: "top-end",
      showConfirmButton: false,
      timer: 3000,
      timerProgressBar: true,
      didOpen: (toast) => {
        toast.onmouseenter = Swal.stopTimer;
        toast.onmouseleave = Swal.resumeTimer;
      }
    });

    let prenom: string =                 $('#prenom').val() as string;
      let nationalite: number =          $('#nat').val() as number;
      let tel: string =                  $('#tel').val() as string;
      let adress: string =               $('#address').val() as string;
      let typeIdentification: number =   $('#typeId').val() as number;
      let identification: string =       $('#identifiant').val() as string;
      let societe: number =              $('#ste').val() as number; 
      let hotelId = this.user?.hotel?.hotelId;
    
    if(
      typeof prenom === 'undefined' || prenom === null || prenom === ""
      || typeof nationalite === 'undefined' || nationalite === null
      || typeof typeIdentification === 'undefined' || typeIdentification === null
      || typeof identification === 'undefined' || identification === null || identification === ""
      || typeof hotelId === 'undefined' || hotelId === null
    ){
      alert("Donnée manquante ou invalide");
    }else{
      let compteForm = {
        compteStatus: 0
      }
      await this.compteService.addCompte(compteForm).pipe(first()).subscribe(async compte => {
        let formClient = {
          prenom:              prenom,
          nationalite:         nationalite,
          tel:                 tel,
          adress:              adress,
          typeIdentification:  typeIdentification,
          identification:      identification,
          compteId:            compte.id,
          hotelId:             hotelId
        }
        await this.clientService.addClient(formClient).pipe(first()).subscribe(client => {
          this.addClient(client);
          this.clients.push(client);
          Toast.fire({
            icon: "success",
            title: "Client ajouté"
          });
        });
      })
      
      
    }
    
  }

  addSociete(societe: Societe){
      societe.hotelId = this.user?.hotel?.hotelId;
      this.societe = societe;
      $('#ste').val(societe.societeNom);
      
      console.log(this.societe);
 }

 async createSociete(){
     const Toast = Swal.mixin({
         toast: true,
         position: "top-end",
         showConfirmButton: false,
         timer: 3000,
         timerProgressBar: true,
         didOpen: (toast) => {
           toast.onmouseenter = Swal.stopTimer;
           toast.onmouseleave = Swal.resumeTimer;
         }
       });
 
       let compteForm = {
         compteStatus: 0
       }
       await this.compteService.addCompte(compteForm).pipe(first()).subscribe(async compte => {
         //user hotel id
         let hotelId = this.user?.hotel?.hotelId;
         //get hotel by id
         let hotel = await this.hotelService.getHotelById(hotelId).pipe(first()).subscribe(async hotel => {
           
         let steObj = {
           societeNom: $('#ste').val(),
           societeCompteId: compte.id,
           hotel: hotel
         }
         this.societeService.addSociete(steObj).pipe(first()).subscribe(societe => {
             this.societes.push(societe);
             this.addSociete(societe);
             Toast.fire({
                 icon: "success",
                 title: "Societe ajoutée"
             });
         });
         }
         );
 
       })
     
   }

   changeReduction(event: Event){
    let reduction:number = $('#reduction').val() as number;
    console.log("Nouvelle reduction: " + reduction)
    this.reductionReservation = (typeof reduction !== 'undefined' && reduction !== null && reduction >= 1 && reduction <= 100) ? reduction : 0;
    this.calculateReservationTotal();
  }

  cancelReservation(){
    if(confirm("Voulez-vous quitter la page? Vos modifications seront perdues")){
      $('#reservModal').modal('hide');
      this.clearSelection();
    }
  }

//CREATION D'UNE RESERVATION
async createReservation() {
    this.isCreatingReservation = true;
    const Toast = Swal.mixin({
      toast: true,
      position: "top-end",
      showConfirmButton: false,
      timer: 3000,
      timerProgressBar: true,
      didOpen: (toast) => {
        toast.onmouseenter = Swal.stopTimer;
        toast.onmouseleave = Swal.resumeTimer;
      }
    });
  
    let ddebut: string = $('#currentReservationStartDate').text() as string;
    let dfin: string = $('#currentReservationEndDate').text() as string;
    let reduction: number = $('#reduction').val() as number;
  
    if (!ddebut || !dfin || this.addedChambres.length === 0 || this.addedClients.length === 0) {
      alert("Vérifiez les données de réservation !");
      return;
    }
  
    if (this.user && this.user.id) {
      let userId: number = this.user.id;
  
      // Création de la réservation
      let formReservation = {
        reservationDateDebut: new Date(ddebut),
        reservationDateFin: new Date(dfin),
        reservationDate: new Date(),
        reservationReduction: reduction,
        reservationStatus: 0,
        reservationUserId: userId,
        reservationHotelId: this.user.hotel?.hotelId
      };
  
      await this.reservationService.addReservation(formReservation).pipe(first()).subscribe(async reservation => {
        let reservationId: number = reservation.reservationId;
        let thisCompo = this;
        // Création de la facture associée à la réservation
        let factureId!: number | undefined;
        await this.factureService.addFacture({
          montant: this.montantReservation,
          dateCreation: new Date(),
          statutPaiement: "UNPAID",  // "PAID", "PARTIALLY_PAID", "UNPAID"
          montantRestant: this.montantReservation,
          reservationId: reservationId,
          idClient: this.addedClients[0].id,
          hotelId: this.user?.hotel?.hotelId
        }).pipe(first()).subscribe(async facture => {
            factureId = facture.factureId;
                    // Fusion des boucles pour gérer les nuitées, lignes de facture, et opérations de compte
        let tickNuitte = new Date(ddebut);
        while (tickNuitte.getTime() <= new Date(dfin).getTime()) {
          // Déterminer l'état (clôturé ou provisoire)
          const isProvisoire = tickNuitte.getTime() >= new Date().getTime();
          const etat = isProvisoire ? "P" : "C";
  
          // Créer une nuitée
          
            await this.nuiteeService.create({
                reservationId: reservationId,
                dateNuit: tickNuitte,
                prix: this.montantReservation / this.periodeReservation,
                facturableId: 3,
                time: tickNuitte.getTime()
              }).pipe(first()).subscribe( async nuitee => {
                // Créer une ligne de facture
            await this.ligneFactureService.create({
              factureId: factureId,
              facturableId: 3,
              nuiteeId: nuitee.id,
              quantite: 1,
              reservationId: reservationId,
              sousTotal: this.montantReservation / this.periodeReservation,
              dateLigneFacture: new Date(),
              montantRestant: this.montantReservation / this.periodeReservation,
              montantPaye: 0.0,
              statutPaiement: "UNPAID",
              etat: etat,
            }).pipe(first()).subscribe( async ligneFacture => {
              // Créer une opération de compte
            await this.operationCompteService.addOperationCompte({
              operationCompteType: 0, // Débit
              operationCompteMontant: this.montantReservation / this.periodeReservation,
              operationCompteMotif: 1,
              operationCompteDate: new Date(),
              operationCompteCompte: this.addedClients[0].compte.id,
              operationCompteUser: userId,
              operationCompteDesc: "Réservation de chambre " + this.addedChambres[0].chambreType.typeChambreLibele + "/" + this.addedChambres[0].chambreNum,
              reservationId: reservationId,
              ligneFactureId: ligneFacture.id,
              factureId: factureId,
              montantPaye: 0.0,
              statutPaiement: "UNPAID",
              etat: etat,
              montantRestant: this.montantReservation / this.periodeReservation
            }).pipe(first()).subscribe();
            }
            );
              }
              );
  
          // Avancer au jour suivant
          tickNuitte.setTime(tickNuitte.getTime() + 60 * 60 * 1000 * 24);
        }
          }
        ); 
  

  
        // Ajout des clients à la réservation
        for (const client of this.addedClients) {
          let clientForm = {
            clientId: client.id,
            chambreId: this.addedChambres[0].id,
            reservationId: reservationId,
            isPayant: 0,
            percentClient: 0,
            hotelId: thisCompo.user?.hotel?.hotelId,
            societeId: thisCompo.societe?.id
          };

          console.log("clientForm from component");
          console.log(clientForm);

          await this.reservationClientService.addReservationClient(clientForm).pipe(first()).toPromise();
          await this.chambreService.toBookChambreById(clientForm.chambreId).pipe(first()).toPromise();
        }
  
        //SI SOCIETE:
        /*if(thisCompo.societe 
          && thisCompo.societe !== null
          && thisCompo.societe.id !== null
          && typeof thisCompo.societe !== 'undefined'){
           //INSERER CLIENT-SOCIETE
           this.addedClients.forEach(async function (client) {
             let societeClientForm = {
               idClient: client.id,
               idSociete: thisCompo.societe.id
             }
             await thisCompo.societeClientService.addSocieteClient(societeClientForm).subscribe();
           })
           
           //INSERER RESERVATION-SOCIETE
           let steReservForm = {
             idSociete: thisCompo.societe.id,	
             idReservation: reservationId
           }
           await thisCompo.societeReservationService.addSocieteReservation(steReservForm).subscribe();
       }*/

       $('#reservModal').modal('hide');
       //this.isCreatingReservation = false;
        Toast.fire({
          icon: "success",
          title: "Réservation ajoutée"
        });
        //this.router.navigateByUrl('reservations');
        this.isCreatingReservation = false;
        //this.cdRef.detectChanges();
        this.loadData();
        
      });
    }
  }


  async consultReservation(reservatinClientId: number){
    this.consultLoading = true;
      this.cdRef.detectChanges();    
      let idReservConsult = reservatinClientId; //$('#consultReservId').text();
      this.reservationClientService.getReservationClientById(idReservConsult).pipe(first()).subscribe(async (reservCltChmb) => {
        console.log(reservCltChmb);
        this.currentConsultReservCltChmb = reservCltChmb;
        if (reservCltChmb.reservation)
          //let facturesClient = reservCltChmb.reservation.factures.filter( facture => facture.typePayant = "s");
          await this.factureService.getFirstFactureByReservationId(reservCltChmb.reservation.reservationId).pipe(first()).subscribe(facture => {
            this.factureConsult = facture;
          });
        if (reservCltChmb.reservation) {
          this.loadSousTotalSumC(reservCltChmb.reservation.reservationId);
          this.loadGestionFactureReservations(reservCltChmb.reservation.reservationId);
          console.log("### gestionFactureReservations ###: ");
          console.log(this.gestionFactureReservations);
          console.log("### gestionFactureReservations ###: ");
          console.log(this.totalAffectationSum);
          this.loadMontantRestantSum(reservCltChmb.reservation.reservationId);
        }
        
        console.log(this.factureConsult);
  
        this.consultLoading = false;

        //$('#consultReservModal').modal('show');
        this.cdRef.detectChanges();
      })    
    }


    loadSousTotalSumC(reservationId: number): void {
      this.gestionFactureReservationService.getSumSousTotalWithEtatC(reservationId).subscribe(
        (data) => {
          this.sousTotalSumC = data; // Utilise uniquement les lignes avec état 'c'
        },
        (error) => {
          console.error('Failed to load sum of sous_total for closed lines', error);
        }
      );
     }
  
     loadGestionFactureReservations(reservationId: number): void {
      console.log("ID reservationnn" + reservationId);
      this.gestionFactureReservationService.getByReservationId(reservationId).subscribe(
          (data) => {
              console.log("### data data data ###: ");
              console.log(data);
              this.gestionFactureReservations = data;
              this.totalAffectationSum = this.gestionFactureReservations.reduce((sum, item) => sum + item.totalaffectation, 0);
          
          },
          (error) => {
              console.error('Failed to load GestionFactureReservations', error);
          }
      );
    }
  
    loadMontantRestantSum(reservationId: number): void {
      this.gestionFactureReservationService.getSumMontantRestant(reservationId).subscribe(
          (data) => {
              this.montantRestantSum = data;
          },
          (error) => {
              console.error('Failed to load sum of montant_restant', error);
          }
      );
  }

  closeModifyReservationModal(): void {
    $('#modifyReservationModal').modal('hide');
  }

  openModifyReservationModal(resCltChm: ReservationClient | undefined): void {
    console.log("openModifyReservationModal appelé ")
    // Charger les données actuelles de la réservation
    this.inModifReservCltChmb = resCltChm;
    if(resCltChm && resCltChm?.reservation){
      this.selectedReservation = resCltChm?.reservation;  
      this.modifyReservationForm = this.fb.group({
        modDateDebut: ['', Validators.required],
        modDateFin: ['', Validators.required],
        modReduction: [0],
        modChambreId: [null, Validators.required],
        modClientId: [null, Validators.required],
        modSocieteId: [null, Validators.required]
      });
  
      this.modifyReservationForm.patchValue({
        modDateDebut: this.formatDate(resCltChm.reservation.reservationDateDebut),
        modDateFin: this.formatDate(resCltChm.reservation.reservationDateFin),
        modReduction: resCltChm.reservation.reservationReduction || 0,
        modChambreId: resCltChm?.chambre?.id,
        modClientId: resCltChm.client?.id,
        modSocieteId: resCltChm.societe?.id
      });
  
      this.newModClientId = resCltChm.client?.id ?? 0;
      this.newModChambreId = resCltChm.chambre?.id ?? 0;
      this.newModSocieteId = resCltChm.societe?.id ?? 0;
  
      this.cdRef.detectChanges();
  
    }
    // Ouvrir le modal
    $('#modifyReservationModal').modal('show');
    
  }

  updateReservation(): void {
    this.isCreatingReservation = true;
    
    if (this.modifyReservationForm.invalid) {
      alert('Veuillez remplir tous les champs correctement.');
      return;
    }
  
    let ddebut: string = $('#modDateDebut').val() as string;
    let dfin: string = $('#modDateFin').val() as string;
    let reduction: number = $('#modReduction').val() as number;

    const formValues = this.modifyReservationForm.value;
      //check if the form values, console log them
      console.log("formValues (modification): ");
      //console.log(formValues);
  
      //console newModClientId, newModChambreId, newModSocieteId
      console.log("newModClientId: ");
      console.log(this.newModClientId);
      console.log("newModChambreId: ");
      console.log(this.newModChambreId);
      console.log("newModSocieteId: ");
      console.log(this.newModSocieteId);
  
      const updatedReservation: UpdatedReservation = {
        reservationId: this.selectedReservation.reservationId,
        //dateDebut: new Date(formValues.modDateDebut),
        dateDebut: new Date(ddebut),
        //dateFin: new Date(formValues.modDateFin),
        dateFin: new Date(dfin),
        //reduction: formValues.modReduction,
        reduction: reduction,
        chambreId: this.newModChambreId,
        clientId: this.newModClientId,
        societeId: this.newModSocieteId
      };
  
      console.log("updatedReservation: ");
      console.log(updatedReservation);

      let that = this;
      this.reservationService.updateReservation(updatedReservation)
        .pipe(first())
        .subscribe(
          (response) => {
            this.isCreatingReservation = false;
            this.cdRef.detectChanges();
            Swal.fire({
              icon: 'success',
              title: 'Réservation mise à jour avec succès',
              timer: 3000
            });
            $('#modifyReservationModal').modal('hide');
            
            // Rechargez la liste ou naviguez selon votre logique
          },
          (error) => {
            console.error('Erreur lors de la mise à jour de la réservation', error);
            alert('Une erreur est survenue. Vérifiez les données !');
            this.isCreatingReservation = false;
          }
        );
  }
  
  // Méthode utilitaire pour formater une date en 'yyyy-MM-dd' pour les inputs de type date
  private formatDate(date: Date): string {
    if (!date) {
      return '';
    }
    const d = new Date(date);
    const month = '' + (d.getMonth() + 1);
    const day = '' + d.getDate();
    const year = d.getFullYear();
    return [year, month.padStart(2, '0'), day.padStart(2, '0')].join('-');
  }

  selectChambre(chambre: Chambre): void {
    // Vérifier si une chambre a déjà été ajoutée
    if (this.addedChambres.length >= 1) {
      // Remettre la chambre précédemment ajoutée dans la liste des chambres disponibles
      this.rooms.push(this.addedChambres[0]);
    }
  
    // Réinitialiser la liste des chambres ajoutées et ajouter la chambre sélectionnée
    this.addedChambres = [];
    this.addedChambres.push(chambre);
  
    // Supprimer la chambre sélectionnée de la liste des chambres disponibles
    this.rooms = this.rooms.filter((chm) => chm.id !== chambre.id);
  
    // Calculer le total de la réservation en fonction de la période et de la réduction
    this.calculateReservationTotal();
  }
  
  onSelectChambre(event: Event): void {
    const selectedChambre = (event.target as HTMLSelectElement).value;
    const chambre = this.rooms.find(ch => ch.id === +selectedChambre);
  
    if (chambre) {
      this.selectChambre(chambre); // Appelle la méthode existante pour ajouter la chambre
    }
  }
  
    changeNewModClient(client: Client) {
      this.newModClientId = client.id;
    }
  
    changeNewModChambre(chambre: Chambre) {
      this.newModClientId = chambre.id;
    }
  
    changeNewModSociete(societe: Societe) {
      console.log("changeNewModSociete");
      //console log the societe
      console.log(societe);
      this.newModClientId = societe.id;
    }

    reloadComponent() {
      window.location.reload();
    }

    navigateToModifNuitees(): void {
      const reservationId = this.currentConsultReservCltChmb?.reservation?.reservationId;
        if (reservationId) {
          // Fermer le modal
          $('#consultReservModal').modal('hide');
          
          // Naviguer vers le composant de modification
          this.router.navigate(['/modif-nuitees', reservationId]);
        } else {
          Swal.fire('Erreur', 'ID de réservation non trouvé', 'error');
        }
    }

}
