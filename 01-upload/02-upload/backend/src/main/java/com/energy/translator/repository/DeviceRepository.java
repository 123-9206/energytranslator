package com.energy.translator.repository;

import com.energy.translator.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceRepository extends MongoRepository<Device, String> {
    
    Page<Device> findByEnterpriseId(String enterpriseId, Pageable pageable);
    
    List<Device> findByEnterpriseId(String enterpriseId);
    
    Page<Device> findBySiteId(String siteId, Pageable pageable);
    
    List<Device> findByEnterpriseIdAndStatus(String enterpriseId, String status);
    
    long countByEnterpriseId(String enterpriseId);

    long countByStatus(String status);
}
