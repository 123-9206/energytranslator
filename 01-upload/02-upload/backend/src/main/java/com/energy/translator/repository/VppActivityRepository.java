package com.energy.translator.repository;

import com.energy.translator.entity.VppActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface VppActivityRepository extends MongoRepository<VppActivity, String> {
    
    Page<VppActivity> findByStatus(String status, Pageable pageable);
    
    Page<VppActivity> findByType(String type, Pageable pageable);
    
    @Query("{ 'time.startAt': { $gte: ?0 }, 'status': { $in: ['registration', 'confirmed'] } }")
    List<VppActivity> findUpcomingActivities(Instant fromTime);
    
    @Query("{ 'time.startAt': { $gte: ?0, $lte: ?1 } }")
    List<VppActivity> findByTimeRange(Instant start, Instant end);
}
