package com.cityprojects.citybackend.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requete de mise a jour d'un mapping comptable hotel.
 *
 * <p>Le {@code typeEvenement} arrive en path variable du controller, donc
 * non inclus dans ce DTO. Seul le {@code compteCode} est modifiable :
 * le service valide qu'il existe dans le PCG et est utilisable.</p>
 */
public record CompteMappingUpdateDto(
        @NotBlank @Size(min = 1, max = 10) String compteCode
) {}
