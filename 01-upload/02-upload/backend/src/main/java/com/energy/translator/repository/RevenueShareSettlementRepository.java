package com.energy.translator.repository;

import com.energy.translator.entity.RevenueShareSettlement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RevenueShareSettlementRepository extends MongoRepository<RevenueShareSettlement, String> {

    List<RevenueShareSettlement> findByEnterpriseId(String enterpriseId);

    List<RevenueShareSettlement> findByPartnerId(String partnerId);
}
