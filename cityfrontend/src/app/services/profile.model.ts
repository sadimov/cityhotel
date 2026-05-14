/**
 * Modèles TypeScript pour le profil self-service de l'utilisateur courant.
 *
 * Aligné sur les DTOs backend (`citybackend/.../dto/profile/`) :
 *  - `ProfileDto`         → `Profile`
 *  - `ProfileUpdateDto`   → `ProfileUpdate`
 *  - `ChangePasswordDto`  → `ChangePasswordRequest`
 *
 * NB : l'`avatarUrl` est relatif côté backend (ex. `/uploads/avatars/user-42-uuid.jpg`).
 * Le front concat avec `environment.apiUrl` pour produire l'URL absolue.
 */

export interface Profile {
  userId: number;
  username: string;
  email: string;
  prenom: string;
  nom: string;
  nomComplet?: string;
  telephone?: string;
  poste?: string;
  hotelNom: string;
  roleCode: string;
  roleNom: string;
  /** Chemin relatif côté serveur (`/uploads/avatars/...`) ou `null` si pas d'avatar. */
  avatarUrl: string | null;
  /** Format ISO 8601 ou `null`. */
  derniereConnexion: string | null;
  motPasseTemporaire: boolean;
}

export interface ProfileUpdate {
  prenom: string;
  nom: string;
  telephone?: string;
  poste?: string;
}

export interface ChangePasswordRequest {
  ancienMotDePasse: string;
  nouveauMotDePasse: string;
  confirmation: string;
}
