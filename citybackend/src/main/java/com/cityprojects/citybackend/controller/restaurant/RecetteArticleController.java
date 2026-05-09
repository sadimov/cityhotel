package com.cityprojects.citybackend.controller.restaurant;

import com.cityprojects.citybackend.dto.restaurant.LigneRecetteDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleCreateDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleDto;
import com.cityprojects.citybackend.service.restaurant.RecetteArticleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API des recettes d'articles (Tour 25).
 *
 * <p>Roles :
 * <ul>
 *   <li>Mutation : SUPERADMIN, ADMIN, GERANT, RESTAURANT (saisie chef).</li>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT, RESTAURANT, MAGASIN
 *       (MAGASIN inclus pour verifier les consommations a venir cote stock).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/restaurant/recettes")
public class RecetteArticleController {

    private final RecetteArticleService service;

    public RecetteArticleController(RecetteArticleService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT','MAGASIN')")
    public ResponseEntity<RecetteArticleDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/article/{articleId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT','MAGASIN')")
    public ResponseEntity<List<RecetteArticleDto>> findByArticle(
            @PathVariable("articleId") Long articleId) {
        return ResponseEntity.ok(service.findAllByArticle(articleId));
    }

    @GetMapping("/article/{articleId}/active")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT','MAGASIN')")
    public ResponseEntity<List<RecetteArticleDto>> findActiveByArticle(
            @PathVariable("articleId") Long articleId) {
        return ResponseEntity.ok(service.findActiveByArticle(articleId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<RecetteArticleDto> create(@Valid @RequestBody RecetteArticleCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<RecetteArticleDto> update(@PathVariable("id") Long id,
                                                    @Valid @RequestBody RecetteArticleCreateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PutMapping("/article/{articleId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<List<RecetteArticleDto>> setRecetteForArticle(
            @PathVariable("articleId") Long articleId,
            @Valid @RequestBody List<LigneRecetteDto> lignes) {
        return ResponseEntity.ok(service.setRecetteForArticle(articleId, lignes));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
