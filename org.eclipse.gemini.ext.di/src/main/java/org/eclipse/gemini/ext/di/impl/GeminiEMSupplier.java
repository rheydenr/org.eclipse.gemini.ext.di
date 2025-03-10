/*******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Filipp A. - Initial implementation
 *******************************************************************************/
package org.eclipse.gemini.ext.di.impl;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;

import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.e4.core.di.suppliers.ExtendedObjectSupplier;
import org.eclipse.e4.core.di.suppliers.IObjectDescriptor;
import org.eclipse.e4.core.di.suppliers.IRequestor;
import org.eclipse.gemini.ext.di.GeminiPersistenceContext;
import org.eclipse.gemini.ext.di.GeminiPersistenceProperty;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;

@Component(immediate = true, name="geminiEM",
property = {"dependency.injection.annotation:String=org.eclipse.gemini.ext.di.GeminiPersistenceContext"},
service = ExtendedObjectSupplier.class)
public class GeminiEMSupplier extends GeminiEMFSupplier {

    @Override
    public Object get(IObjectDescriptor descriptor, IRequestor requestor, boolean track, boolean group) {
        storeRequestor(getUnitName(descriptor, requestor.getRequestingObjectClass()), requestor);
        EntityManagerFactory emf = getEMF(getUnitName(descriptor, requestor.getRequestingObjectClass()),
                getProperties(descriptor, requestor.getRequestingObjectClass()));
        return (emf != null) ? emf.createEntityManager() : null;
    }

    @Override
    protected String getUnitName(IObjectDescriptor descriptor, Class<?> requestingObject) {
        if (descriptor == null) {
            return null;
        }
        GeminiPersistenceContext qualifier = descriptor.getQualifier(GeminiPersistenceContext.class);
        return qualifier.unitName();
    }

    @Override
    protected Map<String, Object> getProperties(IObjectDescriptor descriptor, Class<?> requestingObject) {
        if (descriptor == null) {
            return null;
        }
        GeminiPersistenceContext qualifier = descriptor.getQualifier(GeminiPersistenceContext.class);
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
    
    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.MULTIPLE)
    protected void bindEntityManagerFactory(EntityManagerFactory emf, Map<String, String> properties) {
        super.bindEntityManagerFactory(emf, properties);
    }
    
    protected void unbindEntityManagerFactory(EntityManagerFactory emf, Map<String, String> properties) {
        super.unbindEntityManagerFactory(emf, properties);
    }
    

    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.MULTIPLE)
    protected void bindEntityManagerFactoryBuilder(EntityManagerFactoryBuilder emfb, Map<String, String> properties) {
        super.bindEntityManagerFactoryBuilder(emfb, properties);
    }

    protected void unbindEntityManagerFactoryBuilder(EntityManagerFactoryBuilder emfb, Map<String, String> properties) {
        super.unbindEntityManagerFactoryBuilder(emfb, properties);
    }

    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
    protected void bindPreferencesService(IPreferencesService prefService) {
        super.bindPreferencesService(prefService);
    }

    protected void unbindPreferencesService(IPreferencesService prefService) {
        super.unbindPreferencesService(prefService);
    }
    
}
