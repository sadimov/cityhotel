import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { interval, Subject, takeUntil } from 'rxjs';
import { startWith, switchMap } from 'rxjs/operators';
import Swal from 'sweetalert2';
import { AuthService, UserInfo } from '../../services/auth.service';
import { TranslationService, Language } from '../../services/translation.service';
import { NotificationItem, NotificationsService } from '../services/notifications.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
  standalone: false
})
export class HeaderComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  currentUser: UserInfo | null = null;
  notificationsCount = 0;
  notifications: NotificationItem[] = [];
  showUserDropdown = false;
  showNotifications = false;

  /** Intervalle de polling notifications (60s). */
  private readonly POLL_MS = 60_000;

  constructor(
    private authService: AuthService,
    private router: Router,
    public translationService: TranslationService,
    private notificationsService: NotificationsService,
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
      });

    // Polling dynamique des notifications : appel immediat + refresh chaque 60s.
    interval(this.POLL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.notificationsService.list()),
        takeUntil(this.destroy$),
      )
      .subscribe((items) => {
        this.notifications = items;
        this.notificationsCount = items.length;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Déconnexion : confirmation via SweetAlert2 (le `SweetAlertService` du
   * projet est encore un stub vide — on appelle Swal directement).
   */
  logout(): void {
    Swal.fire({
      title: this.translationService.translate('auth.confirm_logout', 'Êtes-vous sûr de vouloir vous déconnecter ?'),
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: this.translationService.translate('auth.logout', 'Déconnexion'),
      cancelButtonText: this.translationService.translate('common.cancel', 'Annuler'),
      reverseButtons: true,
      customClass: {
        popup: 'swal-popup-custom',
        title: 'swal-title-custom',
        confirmButton: 'btn-primary-city',
        cancelButton: 'btn-primary-city outline'
      },
      buttonsStyling: false
    }).then(result => {
      if (!result.isConfirmed) {
        return;
      }
      this.authService.logout().subscribe({
        next: () => this.router.navigate(['/login']),
        // Forcer la déconnexion locale même en cas d'erreur réseau
        error: () => this.router.navigate(['/login'])
      });
    });
  }

  /** Changer la langue. */
  changeLanguage(language: Language): void {
    this.translationService.setLanguage(language);
  }

  /** Obtenir la langue actuelle. */
  getCurrentLanguage(): Language {
    return this.translationService.getCurrentLanguage();
  }

  /** Avatar de l'utilisateur (chemin disque ou défaut). */
  getUserAvatar(): string {
    if (this.currentUser?.userId) {
      return `assets/images/avatars/user-${this.currentUser.userId}.jpg`;
    }
    return 'assets/images/default-avatar.png';
  }

  /** Logo de l'hôtel courant. */
  getHotelLogo(): string {
    if (this.currentUser?.hotelCode) {
      return `assets/images/hotels/${this.currentUser.hotelCode}/logo.png`;
    }
    return '/assets/images/logo-city-hotel.png';
  }

  /** Naviguer vers le profil. */
  navigateToProfile(): void {
    this.router.navigate(['/profile']);
    this.showUserDropdown = false;
  }

  /** Basculer le dropdown utilisateur. */
  toggleUserDropdown(): void {
    this.showUserDropdown = !this.showUserDropdown;
    this.showNotifications = false;
  }

  /** Basculer le dropdown notifications. */
  toggleNotifications(): void {
    this.showNotifications = !this.showNotifications;
    this.showUserDropdown = false;

    if (this.showNotifications) {
      this.markNotificationsAsRead();
    }
  }

  /** Fermer tous les dropdowns ouverts. */
  closeDropdowns(): void {
    this.showUserDropdown = false;
    this.showNotifications = false;
  }

  /**
   * Marquer les notifications comme lues — V1 = côté UI seulement (pas de
   * persistance backend). Le compteur sera rafraîchi par le polling suivant.
   */
  private markNotificationsAsRead(): void {
    this.notificationsCount = 0;
  }

  /** Navigation depuis une notification cliquée. */
  navigateToNotification(link: string): void {
    this.showNotifications = false;
    if (link) {
      this.router.navigateByUrl(link);
    }
  }

  /** Initiales de l'utilisateur. */
  getUserInitials(): string {
    if (this.currentUser) {
      const firstInitial = this.currentUser.prenom?.charAt(0).toUpperCase() || '';
      const lastInitial  = this.currentUser.nom?.charAt(0).toUpperCase() || '';
      return firstInitial + lastInitial;
    }
    return 'U';
  }

  /**
   * Classe CSS de couleur d'avatar selon le rôle.
   * Map vers les classes propriétaires `avatar-color-*` (palette sky/teal/amber/rose)
   * définies dans `header.component.scss`.
   */
  getAvatarColorClass(): string {
    if (!this.currentUser) return 'avatar-color-default';

    switch (this.currentUser.roleCode) {
      case 'SUPERADMIN': return 'avatar-color-superadmin';
      case 'ADMIN':      return 'avatar-color-admin';
      case 'GERANT':     return 'avatar-color-gerant';
      case 'RECEPTION':  return 'avatar-color-reception';
      case 'RESTAURANT': return 'avatar-color-restaurant';
      case 'RESREC':     return 'avatar-color-resrec';
      case 'MENAGE':     return 'avatar-color-menage';
      case 'MAGASIN':    return 'avatar-color-magasin';
      default:           return 'avatar-color-default';
    }
  }

  /** Fallback image avatar. */
  onImageError(event: Event): void {
    (event.target as HTMLImageElement).src = 'assets/images/default-avatar.png';
  }

  /** Fallback logo hôtel. */
  onHotelLogoError(event: Event): void {
    (event.target as HTMLImageElement).src = 'assets/images/logo-city-hotel.png';
  }
}
