package com.energy.translator.repository;

import com.energy.translator.entity.ControlLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ControlLogRepository extends MongoRepository<ControlLog, String> {

    Page<ControlLog> findByEnterpriseId(String enterpriseId, Pageable pageable);

    Page<ControlLog> findByEnterpriseIdAndDeviceId(String enterpriseId, String deviceId, Pageable pageable);
}
