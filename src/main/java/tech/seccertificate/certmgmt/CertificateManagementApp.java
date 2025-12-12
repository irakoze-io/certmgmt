package tech.seccertificate.certmgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CertificateManagementApp {

    static void main(String[] args) {
        SpringApplication.run(CertificateManagementApp.class, args);
    }

}
