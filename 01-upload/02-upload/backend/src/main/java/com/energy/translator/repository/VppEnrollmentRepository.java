package com.energy.translator.repository;

import com.energy.translator.entity.VppEnrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VppEnrollmentRepository extends MongoRepository<VppEnrollment, String> {
    
    Page<VppEnrollment> findByActivityId(String activityId, Pageable pageable);
    
    Page<VppEnrollment> findByEnterpriseId(String enterpriseId, Pageable pageable);
    
    List<VppEnrollment> findByActivityIdAndStatus(String activityId, String status);
    
    Optional<VppEnrollment> findByActivityIdAndEnterpriseId(String activityId, String enterpriseId);
    
    long countByActivityId(String activityId);
}
