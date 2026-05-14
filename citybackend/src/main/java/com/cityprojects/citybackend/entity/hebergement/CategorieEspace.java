package com.cityprojects.citybackend.entity.hebergement;

/**
 * Categorie fonctionnelle d'un type d'espace reservable.
 *
 * <p>Distingue les <b>chambres</b> (location a la nuitee, occupation 24/24) des
 * <b>salles</b> de conferences/reunions (location a la journee). Les deux
 * partagent l'entite {@link TypeChambre} et la mecanique de reservation
 * existante (Chambre, Reservation, Nuitee, Facture) pour eviter de dupliquer
 * services et repositories.</p>
 *
 * <p>Convention metier : 1 jour de reservation d'une salle = 1 nuitee.</p>
 *
 * <p>Persiste via {@link jakarta.persistence.EnumType#STRING} dans la colonne
 * {@code hebergement.types_chambres.categorie} (CHECK constraint).</p>
 */
public enum CategorieEspace {

    /** Type d'espace chambre (location a la nuitee). */
    CHAMBRE,

    /** Type d'espace salle (conferences, reunions, evenements - location journee). */
    SALLE
}
