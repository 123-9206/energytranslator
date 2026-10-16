package com.energy.translator.repository;

import com.energy.translator.entity.Quota;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuotaRepository extends MongoRepository<Quota, String> {

    List<Quota> findByEnterpriseIdOrderByYearDesc(String enterpriseId);

    Optional<Quota> findByEnterpriseIdAndYear(String enterpriseId, int year);
}
