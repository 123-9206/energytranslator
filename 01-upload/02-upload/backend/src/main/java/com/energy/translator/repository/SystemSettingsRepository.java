package com.energy.translator.repository;

import com.energy.translator.entity.SystemSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemSettingsRepository extends MongoRepository<SystemSettings, String> {
    Optional<SystemSettings> findFirstByOrderByCreatedAtDesc();
}
