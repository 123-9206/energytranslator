package com.energy.translator.repository;

import com.energy.translator.entity.VppResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VppResourceRepository extends MongoRepository<VppResource, String> {
    
    Page<VppResource> findByEnterpriseId(String enterpriseId, Pageable pageable);
    
    List<VppResource> findByEnterpriseIdAndStatus(String enterpriseId, String status);
    
    Page<VppResource> findByStatus(String status, Pageable pageable);
    
    long countByEnterpriseIdAndStatus(String enterpriseId, String status);
}
