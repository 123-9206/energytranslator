package com.energy.translator.repository;

import com.energy.translator.entity.Enterprise;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnterpriseRepository extends MongoRepository<Enterprise, String> {
    
    List<Enterprise> findByStatus(String status);
    
    List<Enterprise> findByName(String name);
}
