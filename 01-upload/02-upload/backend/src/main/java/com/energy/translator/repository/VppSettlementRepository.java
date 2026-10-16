package com.energy.translator.repository;

import com.energy.translator.entity.VppSettlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VppSettlementRepository extends MongoRepository<VppSettlement, String> {
    
    Page<VppSettlement> findByEnterpriseId(String enterpriseId, Pageable pageable);
    
    List<VppSettlement> findByEnterpriseId(String enterpriseId);

    Optional<VppSettlement> findByEnterpriseIdAndPeriod(String enterpriseId, String period);
    
    Page<VppSettlement> findByStatus(String status, Pageable pageable);
}
