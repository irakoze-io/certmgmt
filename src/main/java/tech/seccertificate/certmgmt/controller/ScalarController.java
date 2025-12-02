package tech.seccertificate.certmgmt.controller;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to serve Scalar UI for OpenAPI documentation.
 *
 * <p>This controller serves the Scalar UI HTML page that loads the OpenAPI specification
 * from the /v3/api-docs endpoint. This approach avoids the need for static HTML files
 * and integrates Scalar UI directly into the Spring Boot application.
 *
 * <p>Access Scalar UI at: http://localhost:8080/scalar
 *
 * @author Ivan-Beaudry Irakoze
 * @since Jan 2025
 */
@Controller
public class ScalarController {

    /**
     * Serves the Scalar UI HTML page.
     *
     * @return HTML content with Scalar UI loaded from CDN
     */
    @GetMapping(value = {"/scalar", "/scalar.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public String scalar() {
        return "scalar-ui";
    }
}
