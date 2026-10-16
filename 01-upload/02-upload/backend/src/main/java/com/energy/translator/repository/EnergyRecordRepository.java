package com.energy.translator.repository;

import com.energy.translator.entity.EnergyRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EnergyRecordRepository extends MongoRepository<EnergyRecord, String> {
    
    Page<EnergyRecord> findByEnterpriseId(String enterpriseId, Pageable pageable);
    
    List<EnergyRecord> findByEnterpriseIdAndTimestampBetween(String enterpriseId, Instant start, Instant end);

    Page<EnergyRecord> findByEnterpriseIdAndTimestampBetween(String enterpriseId, Instant start, Instant end, Pageable pageable);
    
    @Query("{ 'enterpriseId': ?0, 'timestamp': { $gte: ?1, $lte: ?2 } }")
    List<EnergyRecord> findByTimeRange(String enterpriseId, Instant start, Instant end);
    
    List<EnergyRecord> findByResourceIdOrderByTimestampDesc(String resourceId, Pageable pageable);

    @Query("{ 'enterpriseId': ?0, 'timestamp': { $gte: ?1, $lte: ?2 }, 'resourceId': ?3 }")
    List<EnergyRecord> findByTimeRangeAndResourceId(String enterpriseId, Instant start, Instant end, String resourceId);

    @Query(value = "{ 'enterpriseId': ?0, 'resourceId': ?1 }", sort = "{ 'timestamp': -1 }")
    List<EnergyRecord> findTop100ByEnterpriseIdAndResourceIdOrderByTimestampDesc(String enterpriseId, String resourceId);

    @Query(value = "{ 'enterpriseId': ?0 }", sort = "{ 'timestamp': -1 }")
    List<EnergyRecord> findTop100ByEnterpriseIdOrderByTimestampDesc(String enterpriseId);

    @Query(value = "{}", sort = "{ 'timestamp': -1 }")
    List<EnergyRecord> findTop100Recent();

    long deleteByTimestampBefore(Instant timestamp);
}
