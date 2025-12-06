package tech.seccertificate.certmgmt.exception;

import tech.seccertificate.certmgmt.entity.Template;

public class TemplateNotFoundException extends ApplicationObjectNotFoundException {
    public TemplateNotFoundException(Template template) {
        super(template);
    }

    public TemplateNotFoundException(Long templateId) {
        super(Template.builder().id(templateId).build());
    }

    public TemplateNotFoundException(String message) {
        super(message);
    }
}
