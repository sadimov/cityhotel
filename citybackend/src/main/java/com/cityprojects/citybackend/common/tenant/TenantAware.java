package com.cityprojects.citybackend.common.tenant;

/**
 * Interface marqueur pour toute entite hotel-scopee.
 * <p>
 * Garantit que l'entite expose un {@code hotelId} lisible/ecrasable par le
 * code transverse (filtres, aspects, listeners) sans recours a la reflection.
 */
public interface TenantAware {

    Long getHotelId();

    void setHotelId(Long hotelId);
}
