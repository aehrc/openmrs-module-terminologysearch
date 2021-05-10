/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.terminologysearchmodule;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.emrapi.EmrApiProperties;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.terminologysearchmodule.htmlformentry.TerminologySearchTagHandler;
import org.openmrs.module.htmlformentry.HtmlFormEntryService;
import org.openmrs.ui.framework.BasicUiUtils;
import org.openmrs.ui.framework.UiUtils;

/**
 * This class contains the logic that is run every time this module is either started or shutdown
 */
public class TerminologySearchActivator extends BaseModuleActivator {
	
	private Log log = LogFactory.getLog(this.getClass());



	/**
	 * Public static so it can be used in tests
	 * @param conceptService
	 * @return
	 */
	public static TerminologySearchTagHandler setupTerminologySearchTagHandler(ConceptService conceptService, UiUtils uiUtils) {
		TerminologySearchTagHandler terminolgySearchTagHandler = new TerminologySearchTagHandler();
		terminolgySearchTagHandler.setConceptService(conceptService);
		terminolgySearchTagHandler.setUiUtils(uiUtils);
		return terminolgySearchTagHandler;
	}

	/**
	 * @see ModuleActivator#willRefreshContext()
	 */
	public void willRefreshContext() {
		log.info("Refreshing Terminolgy Search Module");
	}

	/**
	 * @see ModuleActivator#contextRefreshed()
	 */
	public void contextRefreshed() {
		ConceptService conceptService = Context.getConceptService();
//		DispositionService dispositionService = Context.getRegisteredComponent("dispositionService", DispositionService.class);
//		AdtService adtService = Context.getRegisteredComponent("adtService", AdtService.class);
//		DomainWrapperFactory domainWrapperFactory = Context.getRegisteredComponent("domainWrapperFactory", DomainWrapperFactory.class);

		UiUtils uiUtils = Context.getRegisteredComponent("uiUtils", BasicUiUtils.class);
		if (ModuleFactory.isModuleStarted("htmlformentry")) {
			HtmlFormEntryService htmlFormEntryService = Context.getService(HtmlFormEntryService.class);

			TerminologySearchTagHandler terminolgySearchTagHandler = TerminologySearchActivator.setupTerminologySearchTagHandler(conceptService, uiUtils);
			htmlFormEntryService.addHandler(TerminologySearchConstants.HTMLFORMENTRY_TERMINOLOGY_SEARCH_TAG_NAME, terminolgySearchTagHandler);
		}

		log.info("Terminolgy Search Module refreshed");
	}

	/**
	 * @see ModuleActivator#willStart()
	 */
	public void willStart() {
		log.info("Starting Terminolgy Search Module");
	}

	/**
	 * @see ModuleActivator#started()
	 */
	public void started() {
		log.info("Terminolgy Search Module started");
	}

	/**
	 * @see ModuleActivator#willStop()
	 */
	public void willStop() {
		log.info("Stopping Terminolgy Search Module");
	}

	/**
	 * @see ModuleActivator#stopped()
	 */
	public void stopped() {
		try {
			HtmlFormEntryService htmlFormEntryService = Context.getService(HtmlFormEntryService.class);
			htmlFormEntryService.getHandlers().remove(TerminologySearchConstants.HTMLFORMENTRY_TERMINOLOGY_SEARCH_TAG_NAME);
		} catch (Exception ex) {
			// pass
		}
		log.info("Terminolgy Search Module Stopped");
	}
	
}
