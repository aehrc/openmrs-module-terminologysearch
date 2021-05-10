package org.openmrs.module.terminologysearchmodule.htmlformentry;

import org.openmrs.api.ConceptService;
import org.openmrs.module.htmlformentry.BadFormDesignException;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionController;
import org.openmrs.module.htmlformentry.handler.SubstitutionTagHandler;
import org.openmrs.ui.framework.UiUtils;

import java.util.Map;

public class TerminologySearchTagHandler extends SubstitutionTagHandler {

    private ConceptService conceptService;


    private UiUtils uiUtils;


    public ConceptService getConceptService() {
        return conceptService;
    }

    public void setConceptService(ConceptService conceptService) {
        this.conceptService = conceptService;
    }

    public UiUtils getUiUtils() {
        return uiUtils;
    }

    public void setUiUtils(UiUtils uiUtils) {
        this.uiUtils = uiUtils;
    }

    @Override
    protected String getSubstitution(FormEntrySession session, FormSubmissionController controller, Map<String, String> attributes) throws BadFormDesignException {

    	FormEntryContext context = session.getContext();
    	UiUtils uiUtilsToUse = session.getAttribute("uiUtils") != null ? (UiUtils) session.getAttribute("uiUtils") : this.uiUtils;
        TerminologySearchElement element = new TerminologySearchElement(context, attributes, conceptService, uiUtilsToUse);
        element.setRequired("true".equals(attributes.get("required")));


        controller.addAction(element);
        return element.generateHtml(session.getContext());
    }
}
