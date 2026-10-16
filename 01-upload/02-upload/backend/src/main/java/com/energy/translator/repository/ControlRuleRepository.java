package com.energy.translator.repository;

import com.energy.translator.entity.ControlRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ControlRuleRepository extends MongoRepository<ControlRule, String> {
    
    Page<ControlRule> findByEnterpriseId(String enterpriseId, Pageable pageable);
    
    Page<ControlRule> findByEnterpriseIdAndType(String enterpriseId, String type, Pageable pageable);
}
