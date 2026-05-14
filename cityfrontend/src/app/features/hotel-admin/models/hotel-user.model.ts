/**
 * Modèles TypeScript pour la gestion des utilisateurs par un ADMIN d'hôtel.
 *
 * Alignés sur les DTOs backend (`citybackend/.../dto/admin/`) :
 *  - `DBUserAdminDto`            → `HotelUser`
 *  - `DBUserCreateAdminDto`      → `HotelUserCreate`
 *  - `DBUserUpdateAdminDto`      → `HotelUserUpdate`
 *  - `DBUserResetPasswordResponseDto` → `HotelUserResetPasswordResponse`
 *
 * NB : aucune notion de `hotelId` côté client — le tenant est résolu par
 * le backend via le JWT (`TenantContext`). L'ADMIN ne peut PAS viser un
 * autre hôtel via URL.
 */

export interface HotelUser {
  userId: number;
  username: string;
  email: string;
  prenom: string;
  nom: string;
  nomComplet?: string;
  telephone?: string;
  poste?: string;
  actif?: boolean;
  derniereConnexion?: string | null;
  tentativesConnexion?: number;
  compteVerrouille?: boolean;
  dateCreation?: string | null;
  dateModification?: string | null;
  hotelId?: number;
  hotelCode?: string;
  hotelNom?: string;
  roleId?: number;
  roleCode?: string;
  roleNom?: string;
}

export interface HotelUserCreate {
  username: string;
  email: string;
  password: string;
  prenom: string;
  nom: string;
  telephone?: string;
  poste?: string;
  roleId: number;
}

export interface HotelUserUpdate {
  email?: string;
  prenom?: string;
  nom?: string;
  telephone?: string;
  poste?: string;
  roleId?: number;
}

export interface HotelUserResetPasswordResponse {
  userId: number;
  username: string;
  motDePasseTemporaire: string;
}
