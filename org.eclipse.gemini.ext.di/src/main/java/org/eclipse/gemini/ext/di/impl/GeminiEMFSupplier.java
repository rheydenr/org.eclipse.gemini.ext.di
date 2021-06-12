/*******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Filipp A. - Initial implementation
 *     Nepomuk Seiler - Use of declarative services
 *******************************************************************************/
package org.eclipse.gemini.ext.di.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.persistence.EntityManagerFactory;

import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.e4.core.di.extensions.Preference;
import org.eclipse.e4.core.di.suppliers.ExtendedObjectSupplier;
import org.eclipse.e4.core.di.suppliers.IObjectDescriptor;
import org.eclipse.e4.core.di.suppliers.IRequestor;
import org.eclipse.gemini.ext.di.GeminiPersistenceProperties;
import org.eclipse.gemini.ext.di.GeminiPersistenceProperty;
import org.eclipse.gemini.ext.di.GeminiPersistenceUnit;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;

@Component(immediate = true, name = "geminiEMF", 
property = {"dependency.injection.annotation:String=org.eclipse.gemini.ext.di.GeminiPersistenceUnit"},
service = ExtendedObjectSupplier.class )
public class GeminiEMFSupplier extends ExtendedObjectSupplier {

	private boolean trace = false;
	private final Map<String, EntityManagerFactory> emfs = new HashMap<String, EntityManagerFactory>();
	private final Map<String, EntityManagerFactoryBuilder> emfbs = new HashMap<String, EntityManagerFactoryBuilder>();

	private final Map<String, Set<IRequestor>> requestors = new HashMap<String, Set<IRequestor>>();

	protected IPreferencesService prefService;

	/* ================================================ */
	/* =========== Eclipse e4 DI Extender ============ */
	/* ================================================ */

	@Override
	public Object get(IObjectDescriptor descriptor, IRequestor requestor, boolean track, boolean group) {
		storeRequestor(getUnitName(descriptor, requestor.getRequestingObjectClass()), requestor);
		return getEMF(getUnitName(descriptor, requestor.getRequestingObjectClass()),
				getProperties(descriptor, requestor.getRequestingObjectClass()));
	}

	protected EntityManagerFactory getEMF(String unitName, Map<String, Object> emProperties) {
		Object reinit = emProperties.get(GeminiPersistenceProperties.GEMINI_REINIT);
		if (reinit != null && Boolean.parseBoolean(reinit.toString())) {
			trace("Reinit was set. Reconnecting...");
			System.err.println("REINIT ME!");
			if (emfs.get(unitName) != null) {
				emfs.get(unitName).close();
				emfs.remove(unitName);
			}
		}

		if (emfs.get(unitName) != null) {
			if (emfs.get(unitName).isOpen()) {
				return emfs.get(unitName);
			} else {
				emfs.remove(unitName);
			}
		}

		EntityManagerFactoryBuilder emfb = lookupEntityManagerFactoryBuilder(unitName);
		if (emfb == null) {
			error("EntityManagerFactoryBuilder is null for unit '" + unitName + "' with properties " + emProperties);
			return null;
		}

		// Catch to start up the application, even the db connection failed
		try {
			EntityManagerFactory emf = emfb.createEntityManagerFactory(emProperties);
			emfs.put(unitName, emf);
			return emf;
		} catch (Exception e) {
			error("Could not create EntityManagerFactory for unit '" + unitName + "' with properties " + emProperties, e);
			return null;
		}
	}

	/**
	 * Extracts the persistenceUnit name by searching for the annotation
	 * \@GeminiPersistenceUnit.
	 * 
	 * @param descriptor
	 * @param requestingObject
	 * @return unitName or null
	 */
	protected String getUnitName(IObjectDescriptor descriptor, Class<?> requestingObject) {
		if (descriptor == null) {
			return null;
		}
		GeminiPersistenceUnit qualifier = descriptor.getQualifier(GeminiPersistenceUnit.class);
		return qualifier.unitName();
	}

	/**
	 * Extracts the persistenceUnit name from the service properties map
	 * 
	 * @param properties
	 * @return unitName or null
	 */
	protected String getUnitName(Map<String, String> properties) {
		return properties.get("osgi.unit.name");
	}

	protected Map<String, Object> getProperties(IObjectDescriptor descriptor, Class<?> requestingObject) {
		if (descriptor == null) {
			return null;
		}
		GeminiPersistenceUnit qualifier = descriptor.getQualifier(GeminiPersistenceUnit.class);
		Map<String, Object> properties = new HashMap<String, Object>();
		for (GeminiPersistenceProperty pp : qualifier.properties()) {
			if (pp.valuePref().value().length() > 0) {
				String val = getPreferenceValue(requestingObject, pp);
				properties.put(pp.name(), val);
			} else {
				properties.put(pp.name(), pp.value());
			}
		}
		trace(properties.toString());
		return properties;
	}

	protected String getPreferenceValue(Class<?> requestingObject, GeminiPersistenceProperty pp) {
		return prefService.getString(getNodePath(pp.valuePref(), requestingObject), pp.valuePref().value(), null, null);
	}

	private String getNodePath(Preference preference, Class<?> requestingObject) {
		String nodePath = preference.nodePath();

		if (nodePath == null || nodePath.length() == 0) {
			if (requestingObject == null) {
				return null;
			}
			nodePath = FrameworkUtil.getBundle(requestingObject).getSymbolicName();
		}
		return nodePath;
	}

	public EntityManagerFactoryBuilder lookupEntityManagerFactoryBuilder(String puName) {
		return emfbs.get(puName);
	}

	@PreDestroy
	public void destroy() {
		for (EntityManagerFactory emf : emfs.values()) {
			try {
				if (emf.isOpen()) {
					emf.close();
				}
			} catch (IllegalStateException e) {
				trace("Error while closing EntityManagerFactory." + e.getMessage());
			} catch (Exception e) {
				throw new RuntimeException("Error while closing EntityManagerFactory." + e.getMessage(), e);
			}
		}
		emfs.clear();
	}

	protected void storeRequestor(String pUnit, IRequestor req) {
		if (!requestors.containsKey(pUnit)) {
			requestors.put(pUnit, new HashSet<IRequestor>());
		}

		requestors.get(pUnit).add(req);
	}

	protected void updateRequestors(String pUnit) {
		if (!requestors.containsKey(pUnit)) {
			return;
		}

		Set<IRequestor> reqs = requestors.get(pUnit);
		Set<IRequestor> validReqs = new HashSet<IRequestor>();

		for (IRequestor requestor : reqs) {
			if (requestor.isValid()) {
				try {
					requestor.resolveArguments(false);
					validReqs.add(requestor);
				} catch (IllegalStateException e) {
					error("Error on reinjecting dependencies: " + e.getMessage());
				}

			}
		}
		requestors.put(pUnit, validReqs);
	}

	/* ================================================ */
	/* ======== Declarative Service Component ========= */
	/* ================================================ */

	protected void activate() {
		trace = Boolean.getBoolean("GEMINI_DEBUG");
		trace("service activated");
	}

	@Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.MULTIPLE)
	protected void bindEntityManagerFactory(EntityManagerFactory emf, Map<String, String> properties) {
		trace("bindEntityManagerFactory() with " + properties + " toString: " + emf);
		emfs.put(getUnitName(properties), emf);
		updateRequestors(getUnitName(properties));
	}

	protected void unbindEntityManagerFactory(EntityManagerFactory emf, Map<String, String> properties) {
		trace("unbindEntityManagerFactory() with " + properties);
		if (emf.isOpen()) {
			emf.close();
		}
		emfs.remove(getUnitName(properties));
		updateRequestors(getUnitName(properties));
	}

    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.MULTIPLE)
	protected void bindEntityManagerFactoryBuilder(EntityManagerFactoryBuilder emfb, Map<String, String> properties) {
		trace("bindEntityManagerFactoryBuilder() with " + properties);
		emfbs.put(getUnitName(properties), emfb);
		updateRequestors(getUnitName(properties));
	}

	protected void unbindEntityManagerFactoryBuilder(EntityManagerFactoryBuilder emfb, Map<String, String> properties) {
		trace("unbindEntityManagerFactoryBuilder() with " + properties);
		EntityManagerFactoryBuilder old = emfbs.remove(getUnitName(properties));
		if (old != emfb) {
			throw new RuntimeException("Error while removing EntityManagerFactoryBuilder. Not identical! ");
		}
		updateRequestors(getUnitName(properties));
	}

    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
	protected void bindPreferencesService(IPreferencesService prefService) {
		this.prefService = prefService;
	}

	protected void unbindPreferencesService(IPreferencesService prefService) {
		this.prefService = null;
	}

	/* ================================================ */
	/* ========== Simple Debugging Options ============ */
	/* ================================================ */

	protected void trace(String str) {
		if (!trace) {
			return;
		}
		System.out.println("[GEMINI_EXT][TRACE][" + getClass().getSimpleName() + "] " + str);
	}

	protected void error(String str) {
		System.out.println("[GEMINI_EXT][ERROR][" + getClass().getSimpleName() + "] " + str);
	}

	protected void error(String str, Throwable e) {
		error(str + "\n  Exception message: " + e.getMessage());
	}
}
