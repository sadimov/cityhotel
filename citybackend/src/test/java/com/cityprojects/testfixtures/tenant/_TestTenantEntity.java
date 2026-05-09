package com.cityprojects.testfixtures.tenant;

import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * Fixture de test : entite tenant-aware minimale utilisee uniquement
 * par {@code TenantMultiTenancyTests}.
 * <p>
 * Le prefixe {@code _} signale qu'il ne s'agit pas d'une entite metier
 * mais d'un support de test (pas a copier-coller, pas a integrer).
 * <p>
 * Volontairement place hors du package racine {@code com.cityprojects.citybackend}
 * pour ne PAS etre decouverte par le scan auto de {@code CitybackendApplication}.
 * Cela evite que d'autres tests (ex. {@code CitybackendApplicationTests.contextLoads})
 * ne tentent de valider une table {@code test_tenant_entity} inexistante en
 * environnement reel. Le test qui en a besoin la pointe explicitement via
 * {@link org.springframework.boot.autoconfigure.domain.EntityScan}.
 *
 * <p>Le champ {@code hotelId} est annote
 * {@link org.hibernate.annotations.TenantId} : Hibernate l'utilise comme
 * discriminator de tenant, le populate automatiquement a l'INSERT depuis le
 * {@link com.cityprojects.citybackend.common.tenant.CityTenantIdentifierResolver}
 * et l'ajoute en clause {@code WHERE} de tous les SELECT/UPDATE/DELETE. La
 * setter {@link #setHotelId(Long)} reste presente pour le contrat
 * {@link TenantAware} mais Hibernate n'en a pas besoin pour l'INSERT — il
 * appelle le resolver. Elle ne sert qu'au peuplement manuel hors gestion JPA
 * (peu pertinent ici, vu qu'on s'appuie sur le resolver).</p>
 */
@Entity
@Table(name = "test_tenant_entity")
public class _TestTenantEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String label;

    /**
     * Discriminator de tenant. {@code updatable = false} : Hibernate refuse
     * tout UPDATE sur ce champ (et le rappelle a l'execution si un mapper
     * tentait de le modifier).
     */
    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    public _TestTenantEntity() {
        // requis par JPA
    }

    public _TestTenantEntity(String label, Long hotelId) {
        this.label = label;
        this.hotelId = hotelId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }
}
