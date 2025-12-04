package tech.seccertificate.certmgmt.exception;

import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

public class ApplicationObjectNotFoundException extends RuntimeException {
    public ApplicationObjectNotFoundException(String message) {
        super(message);
    }

    protected ApplicationObjectNotFoundException(Object object) {
        var message = matchClass(object);
        super(message);
    }

    protected ApplicationObjectNotFoundException(Object object, Throwable cause) {
        var message = matchClass(object);
        super(message, cause);
    }

    protected ApplicationObjectNotFoundException(Object object, String message) {
        var innerMessage = matchClass(object);
        super(message + ": " + innerMessage);
    }

    private static <T> String matchClass(T object) {
        var message = "";
        if (object != null) {
            if (object instanceof Certificate certificate) {
                message = "A certificate with identifier " + certificate.getId() + " was not found";
            }

            if (object instanceof Template template) {
                message = "Template with identifier " + template.getId() + " was not found";
            }

            if (object instanceof Customer customer) {
                message = "A customer with identifier " + customer.getId() + " could not be found";
            }

            if (object instanceof TemplateVersion template) {
                message = "Template version with ID " + template.getId() + " was not found";
            }
        }
        return message;
    }
}
