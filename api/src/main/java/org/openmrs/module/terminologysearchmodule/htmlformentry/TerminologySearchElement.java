package org.openmrs.module.terminologysearchmodule.htmlformentry;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptName;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.Obs;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntryContext.Mode;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.element.HtmlGeneratorElement;
import org.openmrs.module.htmlformentry.widget.DropdownWidget;
import org.openmrs.module.htmlformentry.widget.ErrorWidget;
import org.openmrs.module.htmlformentry.widget.HiddenFieldWidget;
import org.openmrs.module.htmlformentry.widget.Option;
import org.openmrs.module.htmlformentry.widget.TextFieldWidget;
import org.openmrs.module.htmlformentry.widget.Widget;
import org.openmrs.module.terminologysearchmodule.TerminologySearchConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.util.LocaleUtility;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TerminologySearchElement implements HtmlGeneratorElement, FormSubmissionControllerAction, CustomFormSubmissionAction {


    private static final Logger LOGGER = LoggerFactory.getLogger(TerminologySearchElement.class);

    private boolean required = false;

    private String id;
    private String clazz;
    private Concept concept;

    private Obs existing = null;

    private String valueLabel;
    private String labelCssClass;

    private ConceptClass conceptClass;

    private Widget valueWidget;
    private Widget hiddenWidget;

    private ErrorWidget errorWidget;

    /*
     *
<div>
    <terminologySearch conceptId="1284" id="termSearch" fhirServerUrl="https://r4.ontoserver.csiro.au/fhir" valueSetUri="https://healthterminologies.gov.au/fhir/ValueSet/clinical-condition-1" />
</div>

http://loinc.org/vs/LL3279-8
Frequency of eating French fries or chips

No, the patient didn’t eat any French fries or chips yesterday	LA22723-3	http://loinc.org
     */
    private Locale locale;

    private UiUtils uiUtils;

    private ConceptService conceptService;

    /**
     * FHIR Terminology server to use
     */
    private String fhirServerUrl = "https://snowstorm-fhir.snomedtools.org/fhir";

    /**
     * FHIR ValueSet URI to use
     */
    private String valueSetUri = "https://healthterminologies.gov.au/fhir/ValueSet/clinical-condition-1";

    private String size = null;

    private int index = 0;

    private int count = 10;
    
    private boolean prefetchAll = false;
    
    private boolean includeDesignations = false;

    private final Map<String, String> systemToConceptSource = new HashMap<String, String>();

    public TerminologySearchElement(FormEntryContext context, Map<String, String> parameters, ConceptService conceptService, UiUtils uiUtils) {
        this.conceptService = conceptService;
        this.uiUtils = uiUtils;

        if (parameters.get("locale") != null) {
            this.locale = LocaleUtility.fromSpecification(parameters.get("locale"));
        }
        else if (uiUtils != null) {
            this.locale = uiUtils.getLocale();
        }
        else {
            this.locale = Context.getLocale();
        }
        
        AdministrationService adminService = Context.getAdministrationService();
        
        String gpDefaultFhirUrl = adminService.getGlobalProperty(TerminologySearchConstants.TERMINOLOGY_SEARCH_DEFAULT_FHIRSERVER_GP);
        if (gpDefaultFhirUrl != null) {
        	fhirServerUrl = gpDefaultFhirUrl;
        }
        String systemToConceptSourceJson = adminService.getGlobalProperty(TerminologySearchConstants.TERMINOLOGY_SEARCH_SYSTEM_TO_CONCEPTSOURCE_GP);
        if (systemToConceptSourceJson != null) {
        	ObjectMapper mapper = new ObjectMapper();
        	try {
				SystemToConceptSource[] mappings = mapper.readValue(systemToConceptSourceJson, SystemToConceptSource[].class);
				for (SystemToConceptSource m : mappings) {
					systemToConceptSource.put(m.system, m.conceptSource);
				}
			} catch (Exception e) {
				LOGGER.error("Failed to parse json in global property '" + TerminologySearchConstants.TERMINOLOGY_SEARCH_SYSTEM_TO_CONCEPTSOURCE_GP 
						+ "' - '" + systemToConceptSourceJson + "'", e );
			}
        }
         

        if (systemToConceptSource.isEmpty()) {
        	// default mappings
            systemToConceptSource.put("http://snomed.info/sct", "SNOMED CT");
            systemToConceptSource.put("http://loinc.org", "LOINC");
			LOGGER.warn("Using default system to conceptSource mappings : 'http://snomed.info/sct'->'SNOMED CT', 'http://loinc.org'->'LOINC'");
        }
        
        
        String conceptId = parameters.get("conceptId");

        if (conceptId == null) {
            throw new RuntimeException("'conceptId' required attribute of terminologySearch tag!");
        }

        concept = HtmlFormEntryUtil.getConcept(conceptId);
        if (concept == null) {
            throw new IllegalArgumentException("Cannot find concept for value " + conceptId
                    + " in conceptId attribute value. Parameters: " + parameters);
        }

        String conceptClasssName = parameters.get("conceptClass");
        conceptClass = conceptService.getConceptClassByName(conceptClasssName);
        if (concept == null) {
            throw new IllegalArgumentException("Cannot find conceptClass for value " + conceptClasssName
                    + " in conceptClass attribute value. Parameters: " + parameters);
        }


        if ("true".equals(parameters.get("required"))) {
            required = true;
        }
        if (parameters.get("id") != null) {
            id = parameters.get("id");
        }
        if (id == null ) {
            throw new RuntimeException("'id' required attribute of terminologySearch tag!");        	
        }
        if (parameters.get("class") != null) {
            clazz = parameters.get("class");
        }

        if (parameters.get("labelCssClass") != null) {
            labelCssClass = parameters.get("labelCssClass");
        }

        if (parameters.get("fhirServerUrl") != null) {
            fhirServerUrl = parameters.get("fhirServerUri");
        }
        else if (parameters.get("fhirServerUri") != null) {     // backwards compatibility
            fhirServerUrl = parameters.get("fhirServerUri");
        }
        if (parameters.get("valueSetUri") != null) {
            valueSetUri = parameters.get("valueSetUri");
        }
        else if (parameters.get("valueSetUrl") != null) {       // backwards compatibility
            valueSetUri = parameters.get("valueSetUrl");
        }

        if (parameters.get("count") != null) {
            count = Integer.parseInt(parameters.get("count"));
        }

        if (parameters.get("index") != null) {
            index = Integer.parseInt(parameters.get("index"));
        }

        if (parameters.get("prefetchAll") != null) {
            prefetchAll = "true".equalsIgnoreCase(parameters.get("prefetchAll"));
        }

        if (parameters.get("includeDesignations") != null) {
        	includeDesignations = "true".equalsIgnoreCase(parameters.get("includeDesignations"));
        }
        
        size = parameters.get("size");

        if (prefetchAll) {
        	 valueWidget =  new DropdownWidget(size == null? null : Integer.valueOf(size));
        	((DropdownWidget)valueWidget).addOption(new Option("...loading...", "", false));
        }
        else {
            valueWidget = new TextFieldWidget(size == null? null : Integer.valueOf(size));
        }
        context.registerWidget(valueWidget);
        errorWidget = new ErrorWidget();
        context.registerErrorWidget(valueWidget, errorWidget);
        hiddenWidget = new HiddenFieldWidget();
        context.registerWidget(hiddenWidget);

        List<Obs> existingObsList = context.getExistingObs().get(concept);
        if (existingObsList != null && !existingObsList.isEmpty() && index < existingObsList.size()) {
            existing = existingObsList.get(index);
            LOGGER.info("Got observation '" + existing + "' with accession " + existing.getAccessionNumber() + " and code " + existing.getValueCoded());
            Concept answerConcept = existing.getValueCoded();
            String conceptName = answerConcept.getName(locale).getName();
            Map<String, String> hiddenMap = new HashMap<>();
            hiddenMap.put("openmrsConceptId", String.valueOf(answerConcept.getId()));
            hiddenMap.put("display", conceptName);
            String hiddenJson;
			try {
				hiddenJson = new ObjectMapper().writeValueAsString(hiddenMap);
				hiddenJson = hiddenJson.replace("\"", "&quot;");
				// hidden field just outputs the value without escaping anything!!
	            hiddenWidget.setInitialValue(hiddenJson);
	            if (prefetchAll) {
	            	((DropdownWidget)valueWidget).addOption(new Option(conceptName, hiddenJson, true));
	            	valueWidget.setInitialValue(hiddenJson);
	            }
			} catch (JsonProcessingException e) {
				LOGGER.error("Failed to encode existing value as json", e);
			}
			if (!prefetchAll) {
				valueWidget.setInitialValue(answerConcept.getName(locale).getName());	
			}
				
            
        }

        String userLocaleStr = locale.toString();

        if (parameters.containsKey("labelNameTag")) {
            if (parameters.get("labelNameTag").equals("default")) {
                valueLabel = concept.getName(locale, false).getName();
            }
            else {
                throw new IllegalArgumentException("Name tags other than 'default' not yet implemented");
            }
        }
        else if (parameters.containsKey("labelText")) {
            valueLabel = parameters.get("labelText");
        }
        else if (parameters.containsKey("labelCode")) {
            valueLabel = context.getTranslator().translate(userLocaleStr, parameters.get("labelCode"));
        }
        else {
            valueLabel = concept.getName(locale, false).getName();
        }
    }

    @Override
    public Collection<FormSubmissionError> validateSubmission(FormEntryContext formEntryContext, HttpServletRequest httpServletRequest) {
        return null;
    }


    @Override
    public String generateHtml(FormEntryContext formEntryContext) {

        StringBuilder ret = new StringBuilder();

        if (formEntryContext.getMode() != Mode.VIEW) {
        	
    		String includeDesStr = includeDesignations ? "true" : "false";
        	if (prefetchAll) {
                Concept answerConcept = existing == null ? null : existing.getValueCoded();
        		String initialValue = answerConcept == null ? "" : answerConcept.getName(locale).getName();
        		ret.append("<script type=\"text/javascript\">\n"
        				+ "  jQuery( function() {\n"
        				+ "       const fhir_server_url = '" + fhirServerUrl.replace("'", "\'") +"';\n"
        				+ "       const value_set_uri = '" + valueSetUri.replace("'", "\'") + "';\n"
        				+ "       const id = '" + id.replace("'", "\'") + "';\n"
        				+ "       const initialValue = '" + initialValue.replace("'", "\'") + "';\n"
        				+ "       const includeDesignations = '" + includeDesStr + "';\n"
        				+ "\n"
        				+ "        var select = getField(id + '.value');\n"
        				+ "	select.change(function(){\n"
        				+ "		const currentVal = select.val();\n"
        				+ "                getField(id + '.code').val(currentVal);\n"
        				+ "	     });\n"
        				+ "\n"
        				+ "	var type = 'GET';\n"
        				+ "        var processData = true;\n"
        				+ "        var url= fhir_server_url + \"/ValueSet/$expand\";\n"
        				+ "        var postData={ 'url': value_set_uri, '_format': 'json', 'includeDesignations': includeDesignations }\n"
        				+ "\n"
        				+ "        var processFunction = function( data ){\n"
        				+ "        	var result = [];\n"
        				+ "        	if (data.expansion && data.expansion.contains){\n"
        				+ "			for (v of data.expansion.contains){\n"
        				+ "				const json = JSON.stringify(v);\n"
        				+ "             if (v.display === initialValue) continue; // don't want same value twice\n"
        				+ "				select.append(jQuery('<option>', { value: json, text: v.display }));\n"
        				+ "			}\n"
        				+ "        	}\n"
        				+ "		select.find('option[value=\"\"]').text(\"-- select -- \");\n"
        				+ "                response( result );\n"
        				+ "        };\n"
        				+ "                                    \n"
        				+ "        jQuery.ajax( {\n"
        				+ "            type: type,\n"
        				+ "            url: url,\n"
        				+ "            processData: processData,\n"
        				+ "            data: postData,\n"
        				+ "            contentType: \"application/json; charset=utf-8\",\n"
        				+ "            dataType: \"json\",\n"
        				+ "            success: processFunction\n"
        				+ "            } );\n"
        				+ "\n"
        				+ "  });\n"
        				+ "</script>\n"
        				+ "");
        	}
        	else {
                ret.append("<script type=\"text/javascript\">\n"
                        + "  jQuery( function() {\n"
                        + "    console.log('Setting up autocomplete for " + id + "');\n"
                        + "    var fhir_server_url = '" + fhirServerUrl + "'; \n"
                        + "    var value_set_uri = '" + valueSetUri + "';\n"
                        + "    var id = '" + id + "';\n"
                        + "    const includeDesignations = '" + includeDesStr + "';\n"
                        + "    getField(id + '.value').autocomplete({\n"
                        + "      source: function( request, response ) {\n"
                        + "                  var type = 'GET';\n"
                        + "                  var processData = true;\n"
                        + "                  var url= fhir_server_url + \"/ValueSet/$expand\";\n"
                        + "                  var postData={ 'url': value_set_uri, 'count':" + count + ", 'filter': request.term, '_format': 'json', 'includeDesignations': includeDesignations }\n"
                        + "                \n"
                        + "                  var processFunction = function( data ){\n"
                        + "                            var result = [];\n"
                        + "                            if (data.expansion && data.expansion.contains){\n"
                        + "                              for (v of data.expansion.contains){\n"
                        + "                                result.push({ 'label' : v.display, 'value' : JSON.stringify(v) });\n"
                        + "                              }\n"
                        + "                            }\n"
                        + "                            response( result );\n"
                        + "                          };\n"
                        + "                \n"
                        + "                  jQuery.ajax( {\n"
                        + "                          type: type,\n"
                        + "                          url: url,\n"
                        + "                          processData: processData,\n"
                        + "                          data: postData,\n"
                        + "                          contentType: \"application/json; charset=utf-8\",\n"
                        + "                          dataType: \"json\",\n"
                        + "                          success: processFunction\n"
                        + "                        } );\n"
                        + "              },\n"
                        + "      select: function( event, ui ) {\n"
                        + "                  event.preventDefault();\n"
                        + "                  if (ui.item.value !== '__NMF__'){\n"
                        + "                    getField(id + '.code').val(ui.item.value);\n"
                        + "                    jQuery(this).val(ui.item.label);\n"
                        + "                    return true;\n"
                        + "                  }\n"
                        + "                  else {\n"
                        + "                    return false;\n"
                        + "                  }\n"
                        + "              },\n"
                        + "      focus: function(event, ui) {\n"
                        + "                 event.preventDefault();\n"
                        + "                 if (ui.item.value !== '__NMF__'){\n"
                        + "                   jQuery(this).val(ui.item.label);\n"
                        + "                 }\n"
                        + "               \n"
                        + "                 return false;\n"
                        + "              },\n"
                        + "      minLength: 2\n"
                        + "    } );\n"
                        + " } );\n"
                        + "</script>");        		
        	}
        }

        if (id != null || clazz != null) {
            ret.append("<span " + (id != null ? "id=\"" + id + "\" " : "") + "class=\"obs-field"
                    + (clazz != null ? " " + clazz : "") + "\">");
            formEntryContext.registerPropertyAccessorInfo(id + ".value", formEntryContext.getFieldNameIfRegistered(valueWidget), null, null, null);
            formEntryContext.registerPropertyAccessorInfo(id + ".error", formEntryContext.getFieldNameIfRegistered(errorWidget), null, null, null);
            formEntryContext.registerPropertyAccessorInfo(id + ".code", formEntryContext.getFieldNameIfRegistered(hiddenWidget), null, null, null);

        }
        if (formEntryContext.getMode() != Mode.VIEW) {
        	ret.append(hiddenWidget.generateHtml(formEntryContext));	
        }
        

        if (labelCssClass != null) {
            ret.append("<span class=\"").append(labelCssClass).append("\">");
        }
        ret.append(valueLabel);
        if (labelCssClass != null) {
            ret.append("</span>");
        }
        if (!"".equals(valueLabel))
            ret.append(" ");
        ret.append(valueWidget.generateHtml(formEntryContext));
        if (formEntryContext.getMode() != Mode.VIEW) {
            // if value is required
            if (required) {
                ret.append("<span class='required'>*</span>");
            }
            ret.append(" ");
            ret.append(errorWidget.generateHtml(formEntryContext));
        }
        if (id != null || clazz != null) {
            ret.append("</span>");
        }
        return ret.toString();


        //        return "<b>THIS IS THE TERMINOLOGY SEARCH MODULE TAG</b>";
    }


    @Override
    public void handleSubmission(FormEntrySession formEntrySession, HttpServletRequest request) {

        FormEntryContext formContext = formEntrySession.getContext();
        String terminologySearchJson = request.getParameter(formContext.getFieldName(hiddenWidget));
        
        
        String terminologySearchValue = request.getParameter(formContext.getFieldName(valueWidget));

        LOGGER.error("Got terminology search value of '" + terminologySearchJson + "' : '" + terminologySearchValue + "'");

        ObjectMapper objectMapper = new ObjectMapper();
        
        Map<String, Object> data;
		try {
			data = objectMapper.readValue(terminologySearchJson, Map.class);
		} catch (Exception e) {
			LOGGER.error("Failed to parse json '" + terminologySearchJson + "'", e);
			return;
		}
        
        Concept answerConcept = null;
        if (data.containsKey("openmrsConceptId")) {
        	// its an existing concept
        	String conceptId = String.valueOf(data.get("openmrsConceptId"));
        	answerConcept = conceptService.getConcept(conceptId);
        }
        else if (data.containsKey("system") && data.containsKey("code")) {
        	String terminologySearchSystem = String.valueOf(data.get("system"));
        	String terminologySearchCode = String.valueOf(data.get("code"));
        	String display = String.valueOf(data.get("display"));

            if (systemToConceptSource.containsKey(terminologySearchSystem)) {
                // first check if the concept exists in the system.
                String sourceName = systemToConceptSource.get(terminologySearchSystem);
                LOGGER.error("Mapping system '" +terminologySearchSystem + "' to source '" + sourceName + "'");

                List<Concept> concepts = conceptService.getConceptsByMapping(terminologySearchCode, sourceName, false);
                
                if (!concepts.isEmpty()) {
                    // find the first 'best match'
                    for (Concept foundConcept : concepts) {
                        for (ConceptMap mapping : foundConcept.getConceptMappings()) {
                            if (mapping.getConceptReferenceTerm().getCode().equals(terminologySearchCode)
                                    && mapping.getConceptReferenceTerm().getConceptSource().getName().equals(sourceName)
                                    && ConceptMapType.SAME_AS_MAP_TYPE_UUID.equals(mapping.getConceptMapType().getUuid())) {
                                answerConcept = foundConcept;
                                LOGGER.error("Found existing concept for  '" +terminologySearchCode + "' from source '" + sourceName + "' : " + answerConcept);

                                break;
                            }
                        }
                    }
                }
                if (answerConcept == null) {
                    // need to create the concept
                    LOGGER.error("No existing concept for  '" +terminologySearchCode + "' from source '" + sourceName + "' : Creating");

                    ConceptSource conceptSource = conceptService.getConceptSourceByName(sourceName);
                    ConceptMapType sameAs = conceptService.getConceptMapTypeByUuid(ConceptMapType.SAME_AS_MAP_TYPE_UUID);

                    ConceptReferenceTerm cft = new ConceptReferenceTerm(conceptSource, terminologySearchCode, display);
                    if (!Context.hasPrivilege(PrivilegeConstants.MANAGE_CONCEPT_REFERENCE_TERMS)) {
                        try {
                            Context.addProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPT_REFERENCE_TERMS);
                            LOGGER.error("Creating ConceptReferenceTerm : " + cft);
                            cft = conceptService.saveConceptReferenceTerm(cft);
                            LOGGER.error("Created ConceptReferenceTerm : " + cft);
                        }
                        finally {
                            Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPT_REFERENCE_TERMS);
                        }
                    }
                    else {
                        // has the privilege
                        LOGGER.error("Creating ConceptReferenceTerm : " + cft);
                        cft = conceptService.saveConceptReferenceTerm(cft);
                        LOGGER.error("Created ConceptReferenceTerm : " + cft);
                    }

                    Concept newConcept = new Concept();
                    Locale languageLocale = new Locale(locale.getLanguage());
                    ConceptName cname = new ConceptName(display, languageLocale);
                    newConcept.setPreferredName(cname);
                    
                    Map<String, ConceptName> addedNames = new HashMap<>();
                    addedNames.put(display, cname);
                    if (data.containsKey("designation")) {
                    	for (Object desObj : (List)data.get("designation")) {
                    		if (desObj instanceof Map) {
                    			
                    			Map des = (Map)desObj;
                    			if (!des.containsKey("value")) {
                    				continue; // no actual designated name
                    			}
                    			String desName = String.valueOf(des.get("value"));
                    			Locale desLocale = languageLocale;
                    			if (des.containsKey("language")) {
                    				desLocale = new Locale(String.valueOf(des.get("language")));
                    			}
                    			ConceptName desConceptName;
                    			if (addedNames.containsKey(desName)) {
                    				desConceptName = addedNames.get(desName);
                    			}
                    			else {
                    				desConceptName = new ConceptName(desName, desLocale);
                    				newConcept.addName(desConceptName);
                    			}
                    			if (des.containsKey("use")) {
                    				Object useObj = des.get("use");
                					if (useObj instanceof Map) {
                						Map use = (Map)useObj;
                						if (use.containsKey("code") && "900000000000003001".equals(use.get("code"))) {
                							// fully specified name
                							newConcept.setFullySpecifiedName(desConceptName);
                						}
                					}
                    			}
                    		}
                    	}
                    }
                    
                    ConceptDatatype cdt = conceptService.getConceptDatatypeByUuid(ConceptDatatype.N_A_UUID);
                    newConcept.setDatatype(cdt);
                    newConcept.setConceptClass(conceptClass);

                    newConcept.addConceptMapping(new ConceptMap(cft, sameAs));
                    if (!Context.hasPrivilege(PrivilegeConstants.MANAGE_CONCEPTS)) {
                        try {
                            Context.addProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPTS);
                            LOGGER.error("Creating Concept : " + newConcept);
                            answerConcept = conceptService.saveConcept(newConcept);
                            LOGGER.error("Created Concept : " + answerConcept);
                        }
                        finally {
                            Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPTS);
                        }
                    }
                    else {
                        // has the privilege
                        LOGGER.error("Creating Concept : " + newConcept);
                        answerConcept = conceptService.saveConcept(newConcept);
                        LOGGER.error("Created Concept : " + answerConcept);
                    }
                }
            }
            else {
                LOGGER.error("Failed to find source for system '" + terminologySearchSystem + "'");
            }
        }


        if (answerConcept != null) {
            if (null == existing) {
                LOGGER.error("Creating observation : " + concept + " with value " + answerConcept);
                formEntrySession.getSubmissionActions().createObs(concept, answerConcept, null, null, null);
            }
            else {
                LOGGER.error("Updating observation : " + concept + " with value " + answerConcept);
                formEntrySession.getSubmissionActions().modifyObs(existing, concept, answerConcept, null, null);
            }
        }
        else if (null != existing) {
            LOGGER.error("Deleting observation : " + concept);
            formEntrySession.getSubmissionActions().modifyObs(existing, concept, null, null, null);
        }
    }


    /**
     * Since 1.27.0
     *
     * @param formEntrySession provides the saved encounter and submitted parameters details
     */
    @Override
    public void applyAction(FormEntrySession formEntrySession) {


    }


    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean getRequired() {
        return required;
    }


    public void setConceptService(ConceptService conceptService) {
        this.conceptService = conceptService;
    }


    public String getFhirServerUri() {
        return fhirServerUrl;
    }

    public void setFhirServerUri(String fhirServerUri) {
        this.fhirServerUrl = fhirServerUri;
    }

    public String getValueSetUrl() {
        return valueSetUri;
    }

    public void setValueSetUrl(String valueSetUrl) {
        this.valueSetUri = valueSetUrl;
    }

    public void setUiUtils(UiUtils uiUtils) {
        this.uiUtils = uiUtils;
    }

    
    public static class SystemToConceptSource {
    	public String system;
    	public String conceptSource;
    }
}
