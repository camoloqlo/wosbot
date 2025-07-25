package cl.camodev.wosbot.almac.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "profile_building")
public class ProfileBuilding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "building_type", nullable = false)
    private BuildingTypeEnum buildingType;

    @Column(name = "current_level", nullable = false)
    private Integer currentLevel;

    @Column(name = "has_internal_upgrade", nullable = false)
    private Boolean hasInternalUpgrade;

    // Constructores
    public ProfileBuilding() {
    }

    public ProfileBuilding(Profile profile, BuildingTypeEnum buildingType, Integer currentLevel,
                           Boolean hasInternalUpgrade) {
        this.profile = profile;
        this.buildingType = buildingType;
        this.currentLevel = currentLevel;
        this.hasInternalUpgrade = hasInternalUpgrade;

    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public BuildingTypeEnum getBuildingType() {
        return buildingType;
    }

    public void setBuildingType(BuildingTypeEnum buildingType) {
        this.buildingType = buildingType;
    }

    public Integer getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(Integer currentLevel) {
        this.currentLevel = currentLevel;
    }

    public Boolean getHasInternalUpgrade() {
        return hasInternalUpgrade;
    }

    public void setHasInternalUpgrade(Boolean hasInternalUpgrade) {
        this.hasInternalUpgrade = hasInternalUpgrade;
    }
}
