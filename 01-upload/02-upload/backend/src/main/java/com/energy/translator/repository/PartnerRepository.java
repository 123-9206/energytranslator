package com.energy.translator.repository;

import com.energy.translator.entity.Partner;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartnerRepository extends MongoRepository<Partner, String> {

    List<Partner> findByEnterpriseId(String enterpriseId);

    List<Partner> findByEnterpriseIdIsNull();

    List<Partner> findByStatus(String status);
}
