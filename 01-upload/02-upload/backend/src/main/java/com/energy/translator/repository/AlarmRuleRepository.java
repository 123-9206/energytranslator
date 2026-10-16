package com.energy.translator.repository;

import com.energy.translator.entity.AlarmRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlarmRuleRepository extends MongoRepository<AlarmRule, String> {

    Page<AlarmRule> findByEnterpriseId(String enterpriseId, Pageable pageable);

    List<AlarmRule> findByEnterpriseIdAndEnabled(String enterpriseId, Boolean enabled);
    
    List<AlarmRule> findByEnabled(Boolean enabled);

    long countByEnterpriseId(String enterpriseId);
}
