package com.energy.translator.repository;

import com.energy.translator.entity.GreenPowerContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GreenPowerContractRepository extends MongoRepository<GreenPowerContract, String> {

    Page<GreenPowerContract> findByEnterpriseId(String enterpriseId, Pageable pageable);

    List<GreenPowerContract> findByEnterpriseId(String enterpriseId);

    List<GreenPowerContract> findByEnterpriseIdAndStatus(String enterpriseId, String status);

    List<GreenPowerContract> findByStatus(String status);

    Optional<GreenPowerContract> findByContractNo(String contractNo);

    Page<GreenPowerContract> findByEnterpriseNameContainingIgnoreCase(String keyword, Pageable pageable);

    Page<GreenPowerContract> findByStatus(String status, Pageable pageable);
}
