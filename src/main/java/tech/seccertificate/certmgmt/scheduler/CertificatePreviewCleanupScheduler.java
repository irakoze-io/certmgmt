package tech.seccertificate.certmgmt.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.seccertificate.certmgmt.service.CertificateService;
import tech.seccertificate.certmgmt.service.CustomerService;
import tech.seccertificate.certmgmt.service.TenantService;

/**
 * Scheduled task to cleanup old certificate preview PDFs.
 * Runs every 5 minutes and deletes preview PDFs older than 15 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificatePreviewCleanupScheduler {

    private static final int CLEANUP_THRESHOLD_MINUTES = 15;

    private final CertificateService certificateService;
    private final CustomerService customerService;
    private final TenantService tenantService;

    /**
     * Cleanup old preview PDFs across all tenant schemas.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupOldPreviews() {
        log.info("Starting scheduled cleanup of certificate preview PDFs older than {} minutes", CLEANUP_THRESHOLD_MINUTES);

        try {
            var customers = customerService.findActiveCustomers();
            var totalCleaned = 0;

            log.debug("Found {} active customers for preview cleanup", customers.size());

            for (var customer : customers) {
                try {
                    var tenantSchema = customer.getTenantSchema();
                    log.debug("Processing preview cleanup for tenant schema: {}", tenantSchema);

                    tenantService.setTenantContext(tenantSchema);

                    var cleanedCount = certificateService.cleanupOldPreviewPdfs(CLEANUP_THRESHOLD_MINUTES);
                    totalCleaned += cleanedCount;

                    if (cleanedCount > 0) {
                        log.info("Cleaned up {} preview PDFs for tenant schema: {}", cleanedCount, tenantSchema);
                    }
                } catch (Exception e) {
                    log.error("Error cleaning up previews for tenant schema {}: {}",
                            customer.getTenantSchema(), e.getMessage(), e);
                } finally {
                    tenantService.clearTenantContext();
                }
            }

            log.info("Scheduled preview cleanup completed. Total PDFs cleaned: {}", totalCleaned);
        } catch (Exception e) {
            log.error("Error during scheduled preview cleanup", e);
        }
    }
}
