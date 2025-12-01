package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.Customer;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByDomain(String domain);

    Optional<Customer> findByTenantSchema(String tenantSchema);

    List<Customer> findByStatus(Customer.CustomerStatus status);

    boolean existsByDomain(String domain);

    boolean existsByTenantSchema(String tenantSchema);
}
