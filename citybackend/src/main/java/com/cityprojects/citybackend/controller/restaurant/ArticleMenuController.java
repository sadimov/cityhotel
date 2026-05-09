package com.cityprojects.citybackend.controller.restaurant;

import com.cityprojects.citybackend.dto.restaurant.ArticleMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuUpdateDto;
import com.cityprojects.citybackend.dto.restaurant.ChangeStatutRequest;
import com.cityprojects.citybackend.service.restaurant.ArticleMenuService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API du catalogue des articles de menu (par hotel).
 *
 * <p>Roles (Tour 23) :
 * <ul>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC, RESTAURANT
 *       (RESTAURANT inclus pour usage POS futur Tour 24+).</li>
 *   <li>Mutation (POST/PUT/PATCH statut) : SUPERADMIN, ADMIN, GERANT, RESTAURANT.</li>
 *   <li>Suppression (deactivate) : SUPERADMIN, ADMIN, GERANT.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/restaurant/articles")
public class ArticleMenuController {

    private final ArticleMenuService service;

    public ArticleMenuController(ArticleMenuService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<ArticleMenuDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<Page<ArticleMenuDto>> search(
            @RequestParam(value = "q", required = false) String recherche,
            @RequestParam(value = "categorieId", required = false) Long categorieId,
            Pageable pageable) {
        return ResponseEntity.ok(service.search(recherche, categorieId, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<ArticleMenuDto> create(@Valid @RequestBody ArticleMenuCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<ArticleMenuDto> update(@PathVariable("id") Long id,
                                                 @Valid @RequestBody ArticleMenuUpdateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<ArticleMenuDto> changeStatut(@PathVariable("id") Long id,
                                                       @Valid @RequestBody ChangeStatutRequest request) {
        return ResponseEntity.ok(service.changeStatut(id, request.statut()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> reactivate(@PathVariable("id") Long id) {
        service.reactivate(id);
        return ResponseEntity.noContent().build();
    }
}
