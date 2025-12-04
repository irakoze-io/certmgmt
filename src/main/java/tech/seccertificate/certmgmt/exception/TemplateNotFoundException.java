package tech.seccertificate.certmgmt.exception;

import tech.seccertificate.certmgmt.entity.Template;

public class TemplateNotFoundException extends ApplicationObjectNotFoundException {
    public TemplateNotFoundException(Template template) {
        super(template);
    }

    public TemplateNotFoundException(Long templateId) {
        var template = Template.builder().id(templateId).build();
        super(template);
    }

    public TemplateNotFoundException(String message) {
        super(message);
    }

}
