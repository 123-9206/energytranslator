package com.energy.translator.repository;

import com.energy.translator.entity.Alarm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlarmRepository extends MongoRepository<Alarm, String> {
    
    Page<Alarm> findByEnterpriseId(String enterpriseId, Pageable pageable);
    
    Page<Alarm> findByEnterpriseIdAndStatus(String enterpriseId, String status, Pageable pageable);

    Page<Alarm> findByEnterpriseIdAndLevel(String enterpriseId, String level, Pageable pageable);

    Page<Alarm> findByEnterpriseIdAndStatusAndLevel(String enterpriseId, String status, String level, Pageable pageable);
    
    long countByEnterpriseId(String enterpriseId);

    long countByStatus(String status);

    long countByEnterpriseIdAndStatus(String enterpriseId, String status);

    long countByEnterpriseIdAndLevel(String enterpriseId, String level);
}
