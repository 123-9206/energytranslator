package com.energy.translator.repository;

import com.energy.translator.entity.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteRepository extends MongoRepository<Site, String> {
    
    Page<Site> findByEnterpriseId(String enterpriseId, Pageable pageable);
}
