package com.cityprojects.citybackend.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une methode service du module menage qui doit etre auditee dans
 * {@code menage.historique} apres execution reussie.
 *
 * <p>Tour 30 etape 5 : remplace les appels manuels a
 * {@code historiqueService.enregistrer(...)} dispersees dans
 * {@code TacheServiceImpl}, et fixe le bug de {@code userId = null} systematique
 * en lisant l'identite depuis le {@code SecurityContext}
 * ({@link com.cityprojects.citybackend.security.UserPrincipal#getUserId()}).</p>
 *
 * <h3>Contrat sur la methode annotee</h3>
 * <ul>
 *   <li>Soit retourne un {@link com.cityprojects.citybackend.dto.menage.TacheDto}
 *       (cas create / update / assigner / commencer / terminer / annuler) :
 *       l'aspect lit {@code tacheId, chambreId, personnelId, statut} sur le DTO.</li>
 *   <li>Soit retourne {@code void} et accepte {@code Long tacheId} en premier
 *       argument (cas {@code delete}) : l'aspect re-resout les ids via le
 *       {@link com.cityprojects.citybackend.repository.menage.TacheRepository}
 *       AVANT l'execution (la tache n'existe plus apres).</li>
 * </ul>
 *
 * <h3>Ordre d'execution AOP</h3>
 * <p>L'aspect doit s'executer <b>a l'interieur</b> de la transaction Spring pour
 * que l'INSERT historique soit commit dans la meme TX que la mutation metier
 * (cas TERMINEE -&gt; historique "fin" : si l'INSERT historique echoue, on doit
 * rollback la TERMINEE). Solution : ordre {@link org.springframework.core.Ordered#LOWEST_PRECEDENCE}
 * pour rester apres l'aspect transactionnel (qui est en LOWEST_PRECEDENCE par
 * defaut, donc on prend la valeur juste au-dessus pour s'executer aux bornes
 * internes de la TX).</p>
 *
 * <h3>Securite</h3>
 * <p>Le {@code userId} est extrait du {@link org.springframework.security.core.context.SecurityContextHolder}
 * via {@link com.cityprojects.citybackend.security.UserPrincipal}. Si le
 * principal n'est pas un {@code UserPrincipal} (ex. tache automatique scheduler,
 * batch, test sans auth), {@code userId} reste {@code null} comme avant.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {

    /**
     * Libelle de l'action ecrit en base ({@code menage.historique.action}).
     * Conserve les valeurs historiques utilisees par le mono ({@code "creation"},
     * {@code "modification"}, {@code "assignation"}, {@code "debut"},
     * {@code "fin"}, {@code "suppression"}, {@code "annulation"}) pour
     * compatibilite des dashboards existants.
     */
    String value();

    /**
     * Indique si l'aspect doit calculer un {@code ancienStatut}/{@code nouveauStatut}
     * a partir du DTO retourne et d'un avant/apres a recuperer en base.
     *
     * <p>Pour les transitions ({@code commencer}, {@code terminer}, {@code annuler}),
     * l'aspect lit l'ancien statut depuis la base AVANT l'execution puis compare
     * au statut du DTO retourne. Pour {@code creation}/{@code modification}/{@code assignation}/{@code suppression},
     * desactiver via {@code transition = false} (l'historique aura
     * {@code ancien_statut}/{@code nouveau_statut} a {@code null} ou identiques).</p>
     */
    boolean transition() default false;

    /**
     * Commentaire libre stocke dans {@code menage.historique.commentaire}.
     * Si vide, l'aspect compose un message a partir du libelle d'action.
     */
    String commentaire() default "";
}
