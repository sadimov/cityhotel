package com.cityprojects.citybackend.service.profile;

import com.cityprojects.citybackend.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Service technique d'encapsulation de la persistance disque des avatars.
 *
 * <h2>Responsabilites</h2>
 * <ul>
 *   <li>Valider le {@link MultipartFile} entrant (type MIME + taille).</li>
 *   <li>Generer un nom de fichier sur (UUID + extension neutre) — pas
 *       d'utilisation du {@code originalFilename} client (anti path traversal).</li>
 *   <li>Ecrire le fichier dans {@code app.uploads.avatars.dir} (cree si absent).</li>
 *   <li>Supprimer un fichier (par filename uniquement, jamais par chemin client).</li>
 * </ul>
 *
 * <h2>Anti-pattern path traversal</h2>
 * <p>{@link #resolveSafe(String)} refuse tout filename contenant {@code "/"},
 * {@code "\\"} ou {@code ".."}. Les filenames sont generes par nous et ne
 * peuvent normalement pas contenir ces caracteres — la garde est defense en
 * profondeur (le code appelant ne doit jamais accepter un filename qui vient
 * du client). Pour eviter le bug "fichier hors du dossier", on resoud le
 * chemin absolu et on verifie qu'il commence par {@link #rootPath()}.</p>
 *
 * <h2>Non-tenant</h2>
 * <p>Service technique sans {@code @RequireTenant} : la garde se fait
 * naturellement au niveau du {@code ProfileService} qui resout l'userId via
 * le {@link com.cityprojects.citybackend.common.security.SecurityUtils} et
 * passe un filename derive de cet userId.</p>
 */
@Service
public class AvatarStorageService {

    private static final Logger logger = LoggerFactory.getLogger(AvatarStorageService.class);

    /** Taille max acceptee cote service (defense en profondeur, complete spring.servlet.multipart). */
    static final long MAX_BYTES = 2L * 1024 * 1024;

    /** Types MIME acceptes (lower-case). */
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp");

    /** Extension par type MIME — on ne fait pas confiance au nom client. */
    private static final java.util.Map<String, String> CONTENT_TYPE_TO_EXT = java.util.Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final Path rootDir;

    public AvatarStorageService(@Value("${app.uploads.avatars.dir:./uploads/avatars}") String rootDir) {
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
    }

    /**
     * Stocke l'avatar pour {@code userId} et renvoie le nom de fichier (relatif
     * a {@link #rootPath()}). L'ancien fichier eventuel (passe en
     * {@code previousFilename}) est supprime si different du nouveau pour eviter
     * la fuite de stockage.
     */
    public String store(Long userId, MultipartFile file, String previousFilename) {
        validate(file);
        ensureRootExists();

        String ext = CONTENT_TYPE_TO_EXT.get(file.getContentType().toLowerCase());
        // UUID elimine les collisions, prefixe user-{id} aide a debugger en operation.
        String filename = "user-" + userId + "-" + UUID.randomUUID() + "." + ext;
        Path target = resolveSafe(filename);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("AvatarStorage: ecriture echouee filename={} userId={}", filename, userId, e);
            throw new BusinessException("error.avatar.storage.failed");
        }

        // Best-effort : nettoyer l'ancien fichier (si different) APRES ecriture
        // reussie pour ne pas se retrouver sans avatar si la copie echoue.
        if (previousFilename != null && !previousFilename.isBlank()
                && !previousFilename.equals(filename)) {
            deleteQuiet(previousFilename);
        }

        return filename;
    }

    /**
     * Supprime le fichier {@code filename} du stockage. Idempotent : ne leve
     * pas si le fichier n'existe pas (cas d'un utilisateur sans avatar).
     */
    public void delete(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        Path target = resolveSafe(filename);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // On log mais on ne fait pas echouer l'operation metier (l'utilisateur
            // a deja perdu la reference cote BDD ; le fichier orphelin sera nettoye
            // par un job de purge eventuel).
            logger.warn("AvatarStorage: delete failed filename={}", filename, e);
        }
    }

    /** Racine absolue ou les avatars sont stockes (testable via Mockito @TempDir). */
    public Path rootPath() {
        return rootDir;
    }

    /**
     * Valide le {@link MultipartFile} : non null, non vide, taille &lt;= 2 MB,
     * content-type dans la whitelist. Leve {@link BusinessException} avec une
     * cle i18n explicite sinon.
     */
    void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("error.avatar.file.empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("error.avatar.file.tooLarge");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_CONTENT_TYPES.contains(ct.toLowerCase())) {
            throw new BusinessException("error.avatar.file.invalidType");
        }
    }

    /**
     * Resout {@code filename} sous {@link #rootDir} en refusant tout chemin
     * qui sortirait du dossier (anti path traversal). Refuse aussi les
     * separateurs et {@code ".."} dans le filename brut.
     */
    Path resolveSafe(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\")
                || filename.contains("..")) {
            throw new BusinessException("error.avatar.file.invalidName");
        }
        Path resolved = rootDir.resolve(filename).normalize();
        if (!resolved.startsWith(rootDir)) {
            // Defense en profondeur : ne devrait jamais arriver vu les guards
            // ci-dessus, mais on bloque explicitement si quelqu'un trafique.
            throw new BusinessException("error.avatar.file.invalidName");
        }
        return resolved;
    }

    private void ensureRootExists() {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            logger.error("AvatarStorage: creation root echouee dir={}", rootDir, e);
            throw new BusinessException("error.avatar.storage.failed");
        }
    }

    private void deleteQuiet(String filename) {
        try {
            Path target = resolveSafe(filename);
            Files.deleteIfExists(target);
        } catch (IOException | RuntimeException e) {
            // best-effort cleanup ; on log seulement (pas de propagation : si la
            // suppression de l'ancien avatar echoue, le nouveau est deja en place,
            // l'orphelin sera nettoye par un job de purge eventuel).
            logger.warn("AvatarStorage: cleanup ancien avatar echoue filename={}", filename, e);
        }
    }
}
