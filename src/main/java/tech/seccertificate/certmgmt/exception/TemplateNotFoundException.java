package tech.seccertificate.certmgmt.exception;

import tech.seccertificate.certmgmt.entity.Template;

public class TemplateNotFoundException extends ApplicationObjectNotFoundException {
    public TemplateNotFoundException(Template template) {
        super(template);
    }

    public TemplateNotFoundException(Template template, String message) {
        super(template, message);
    }
}
