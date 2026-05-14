package com.cityprojects.citybackend.service.profile;

import com.cityprojects.citybackend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire pures (sans Spring) de {@link AvatarStorageService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : validate() refuse type MIME hors whitelist + fichier vide + taille &gt; 2 MB.</li>
 *   <li>T2 : resolveSafe() refuse les filenames avec {@code /}, {@code \\}, {@code ..}
 *       (anti path traversal).</li>
 * </ol>
 */
class AvatarStorageServiceTests {

    @Test
    @DisplayName("T1 - validate() refuse type MIME non liste, fichier vide, taille > 2 MB")
    void shouldRejectInvalidUploads(@TempDir Path tempDir) {
        AvatarStorageService svc = new AvatarStorageService(tempDir.toString());

        // a) fichier null
        BusinessException ex1 = assertThrows(BusinessException.class, () -> svc.validate(null));
        assertEquals("error.avatar.file.empty", ex1.getMessage());

        // b) fichier vide
        MockMultipartFile empty = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]);
        BusinessException ex2 = assertThrows(BusinessException.class, () -> svc.validate(empty));
        assertEquals("error.avatar.file.empty", ex2.getMessage());

        // c) type MIME hors whitelist (text/plain)
        MockMultipartFile txt = new MockMultipartFile("file", "a.txt", "text/plain",
                new byte[]{1, 2, 3, 4});
        BusinessException ex3 = assertThrows(BusinessException.class, () -> svc.validate(txt));
        assertEquals("error.avatar.file.invalidType", ex3.getMessage());

        // d) content-type null
        MockMultipartFile noCt = new MockMultipartFile("file", "a.jpg", null,
                new byte[]{1, 2, 3, 4});
        BusinessException ex4 = assertThrows(BusinessException.class, () -> svc.validate(noCt));
        assertEquals("error.avatar.file.invalidType", ex4.getMessage());

        // e) taille > MAX_BYTES (2 MB)
        byte[] tooBig = new byte[(int) AvatarStorageService.MAX_BYTES + 1];
        MockMultipartFile big = new MockMultipartFile("file", "a.png", "image/png", tooBig);
        BusinessException ex5 = assertThrows(BusinessException.class, () -> svc.validate(big));
        assertEquals("error.avatar.file.tooLarge", ex5.getMessage());

        // Sanity check : un fichier acceptable ne leve pas.
        MockMultipartFile ok = new MockMultipartFile("file", "a.png", "image/png",
                new byte[]{1, 2, 3, 4});
        svc.validate(ok); // pas d'exception
    }

    @Test
    @DisplayName("T2 - resolveSafe() refuse filenames avec '/', '\\\\', '..' (anti path traversal)")
    void shouldRejectPathTraversal(@TempDir Path tempDir) throws Exception {
        AvatarStorageService svc = new AvatarStorageService(tempDir.toString());

        // Cas attendus a refuser
        assertThrows(BusinessException.class, () -> svc.resolveSafe("../evil.png"));
        assertThrows(BusinessException.class, () -> svc.resolveSafe("foo/bar.png"));
        assertThrows(BusinessException.class, () -> svc.resolveSafe("foo\\bar.png"));
        assertThrows(BusinessException.class, () -> svc.resolveSafe("..hidden"));
        assertThrows(BusinessException.class, () -> svc.resolveSafe(""));
        assertThrows(BusinessException.class, () -> svc.resolveSafe(null));

        // Cas legitime : filename UUID-style
        Path resolved = svc.resolveSafe("user-42-abc123.jpg");
        assertTrue(resolved.startsWith(tempDir.toAbsolutePath().normalize()),
                "Le chemin resolu doit etre sous la racine");
        assertEquals("user-42-abc123.jpg", resolved.getFileName().toString());

        // Sanity check : store() puis delete() effacent bien le fichier.
        MockMultipartFile ok = new MockMultipartFile("file", "a.png", "image/png",
                new byte[]{1, 2, 3, 4, 5});
        String filename = svc.store(99L, ok, null);
        Path stored = tempDir.resolve(filename);
        assertTrue(Files.exists(stored), "Le fichier doit exister apres store()");
        svc.delete(filename);
        assertFalse(Files.exists(stored), "Le fichier doit etre supprime apres delete()");
    }
}
