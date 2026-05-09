package com.cityprojects.citybackend.entity.menage;

/**
 * Type de nettoyage sur une tache de menage.
 *
 * <ul>
 *   <li>{@link #QUOTIDIEN} : nettoyage standard d'une chambre occupee /
 *       liberee.</li>
 *   <li>{@link #GRAND_MENAGE} : nettoyage en profondeur (plus long, planifie
 *       periodiquement ou apres une chambre VIP / groupe).</li>
 *   <li>{@link #MAINTENANCE} : intervention technique (plomberie, electricite,
 *       reparation) confiee a l'equipe menage.</li>
 * </ul>
 *
 * <p>Stocke en base sous forme de VARCHAR (cf. changeset 007-m.3).</p>
 */
public enum TypeNettoyage {
    QUOTIDIEN,
    GRAND_MENAGE,
    MAINTENANCE
}
