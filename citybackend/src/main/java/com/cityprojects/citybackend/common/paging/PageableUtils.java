package com.cityprojects.citybackend.common.paging;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utilitaire de manipulation de {@link Pageable} pour le module hebergement
 * (et autres modules a venir).
 *
 * <h3>{@link #stable(Pageable, Sort, String)}</h3>
 * <p>Garantit un tri stable (Tour 14 audit, finding I2) : si l'appelant ne
 * fournit pas de tri, on applique le {@code defaultSort} ; on ajoute toujours
 * un tri secondaire stable {@code id ASC} (cle primaire) en queue pour
 * desambiguer les ex-aequo et ainsi rendre la pagination deterministe entre
 * deux pages successives.</p>
 *
 * <h3>{@link #remapSort(Pageable, Map)}</h3>
 * <p>Remappe les noms de proprietes du {@link Sort} (Tour 14 audit, finding I4).
 * Utile quand le DTO front utilise un alias historique (ex. {@code dateCreation})
 * alors que l'attribut JPA s'appelle differemment (ex. {@code createdAt},
 * herite d'{@code AuditableEntity}). On evite ainsi soit une migration de
 * schema, soit une renomme du champ DTO.</p>
 */
public final class PageableUtils {

    private PageableUtils() {
        // utilitaire pur
    }

    /**
     * Retourne un {@link Pageable} dont le tri est stable :
     * <ul>
     *   <li>si {@code pageable.getSort()} est non vide, on lui ajoute
     *       {@code id ASC} (ou la propriete {@code idProperty}) en queue ;</li>
     *   <li>si vide, on applique {@code defaultSort} + {@code id ASC} en queue.</li>
     * </ul>
     *
     * @param pageable     pageable d'entree (peut etre {@code Pageable.unpaged()} : retourne tel quel)
     * @param defaultSort  tri par defaut si l'utilisateur n'en a pas fourni
     * @param idProperty   nom de la propriete cle primaire utilisee pour le tie-breaker
     * @return un nouveau Pageable avec tri stable garanti
     */
    public static Pageable stable(Pageable pageable, Sort defaultSort, String idProperty) {
        if (pageable.isUnpaged()) {
            return pageable;
        }
        Sort tieBreaker = Sort.by(Sort.Order.asc(idProperty));
        Sort base = pageable.getSort().isSorted() ? pageable.getSort() : defaultSort;
        // Eviter de doubler le tie-breaker s'il est deja present
        boolean alreadyHasIdSort = base.stream().anyMatch(o -> o.getProperty().equals(idProperty));
        Sort effective = alreadyHasIdSort ? base : base.and(tieBreaker);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effective);
    }

    /**
     * Retourne un nouveau {@link Pageable} dont les proprietes du {@link Sort}
     * sont remappees selon {@code aliases} ({@code DTO -> entite}). Les
     * proprietes non listees passent inchangees.
     *
     * <p>Ex. {@code remapSort(p, Map.of("dateCreation", "createdAt"))} : un
     * {@code sort=dateCreation,desc} en entree devient {@code sort=createdAt,desc}.</p>
     */
    public static Pageable remapSort(Pageable pageable, Map<String, String> aliases) {
        if (pageable.isUnpaged() || pageable.getSort().isUnsorted() || aliases.isEmpty()) {
            return pageable;
        }
        List<Sort.Order> remapped = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String resolved = aliases.getOrDefault(order.getProperty(), order.getProperty());
            Sort.Order newOrder = new Sort.Order(order.getDirection(), resolved, order.getNullHandling());
            if (order.isIgnoreCase()) {
                newOrder = newOrder.ignoreCase();
            }
            remapped.add(newOrder);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(remapped));
    }
}
