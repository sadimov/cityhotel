package com.cityprojects.citybackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Configuration Spring MVC.
 *
 * <h2>Avatar serving (Tour A)</h2>
 * <p>Expose {@code /uploads/avatars/**} en static, point vers le repertoire
 * {@code app.uploads.avatars.dir}. Le filename embarque un UUID donc l'URL
 * n'est pas devinable et peut etre publique. Cache 1 heure (les avatars
 * changent peu souvent ; le UUID change a chaque upload donc la cache-busting
 * est automatique).</p>
 *
 * <h2>Arbitrage static vs endpoint auth-protected</h2>
 * <p>Choix static (ce fichier) : simple, performant (Spring sert directement
 * sans passer par la chaine auth), URL non devinable (UUID). Aucune donnee
 * sensible exposee : l'avatar est volontairement public (header / UI cote
 * front). Si un client final veut absolument garder ses avatars privees, on
 * pourra ajouter un controller {@code GET /api/profile/avatars/{filename}}
 * qui valide l'auth et streame le fichier — mais ce n'est pas le besoin
 * commun et ca couterait inutile a chaque chargement de page.</p>
 *
 * <h2>SecurityConfig coordination</h2>
 * <p>{@code SecurityConfig.filterChain} a une regle permitAll() sur
 * {@code /uploads/avatars/**} pour laisser passer ces requetes static sans
 * authentification — cf. modification cojuxte de {@link SecurityConfig}.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.uploads.avatars.dir:./uploads/avatars}")
    private String avatarsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resoudre en chemin absolu pour eviter les surprises de WorkingDir
        // selon le mode de lancement (mvn vs jar vs IDE).
        String absolute = Path.of(avatarsDir).toAbsolutePath().normalize().toString();
        // Spring exige le suffixe "/" pour les locations "file:..."
        String location = "file:" + absolute.replace("\\", "/") + "/";

        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
