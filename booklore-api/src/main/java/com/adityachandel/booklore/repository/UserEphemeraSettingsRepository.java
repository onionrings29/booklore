package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.UserEphemeraSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserEphemeraSettingsRepository extends JpaRepository<UserEphemeraSettingsEntity, Long> {
    Optional<UserEphemeraSettingsEntity> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
