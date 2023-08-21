/*
 * This file is part of the repicea library.
 *
 * Copyright (C) 2009-2021 Mathieu Fortin for Rouge Epicea.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed with the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * Please see the license at http://www.gnu.org/copyleft/lesser.html.
 */
package repicea.simulation.metamodel;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import repicea.io.Loadable;
import repicea.io.Saveable;
import repicea.serial.xml.XmlDeserializer;
import repicea.serial.xml.XmlSerializer;
import repicea.serial.xml.XmlSerializerChangeMonitor;

/**
 * Handle different instances of ExtMetaModel. It is thread safe.
 * @author Mathieu Fortin - December 2020
 */
public class MetaModelManager extends ConcurrentHashMap<String, MetaModel> implements Loadable, Saveable {	
	
	static {
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelManager", 
				"repicea.simulation.metamodel.MetaModelManager");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel", 
				"repicea.simulation.metamodel.MetaModel");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$Bound", 
				"repicea.simulation.metamodel.MetaModel$Bound");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$InnerModel", 
				"repicea.simulation.metamodel.MetaModel$InnerModel");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.ExtScriptResult", 
				"repicea.simulation.metamodel.ScriptResult");				
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.DataBlockWrapper", 
				"repicea.simulation.metamodel.DataBlockWrapper");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelGibbsSample", 
				"repicea.simulation.metamodel.MetaModelGibbsSample");
	}
	
	
	protected static String LoggerName = MetaModelManager.class.getName();

	/**
	 * Constructor.
	 */
	public MetaModelManager() {}	

	/**
	 * Compute and return the prediction generated from a particular meta-model.
	 * @param stratumGroup a String that stands for the stratum group
	 * @param ageYr the age of the stratum (yr) 
	 * @param timeSinceInitialDateYr the time since the initial date (yr)
	 * @return a double
	 * @throws MetaModelException if the model has not been fitted or if the stratum group is not found in the Map.
	 */
	public double getPrediction(String stratumGroup, int ageYr, int timeSinceInitialDateYr) throws MetaModelException {
		MetaModel metaModel = getFittedMetaModel(stratumGroup);
		return metaModel.getPrediction(ageYr, timeSinceInitialDateYr);
	}
	
	private MetaModel getFittedMetaModel(String stratumGroup) throws MetaModelException {
		if (!containsKey(stratumGroup)) {
			throw new MetaModelException("The meta model for this stratum group does not exist: " + stratumGroup);
		} else {
			MetaModel model = get(stratumGroup);
			if (!model.hasConverged()) {
				throw new MetaModelException("The meta model for this stratum group has not been fitted or has not converged: " + stratumGroup);
			}
			return model;
		}
	}
	
	/**
	 * Provide the stratum groups. 
	 * @return a List of String (sorted)
	 */
	public List<String> getStratumGroups() {
		ArrayList<String> list = Collections.list(keys());
		list.sort(null);
		return list;
	}

	/**
	 * Provide the selected output type (e.g. "Coniferous", "Broadleaved") for a particular stratum group.
	 * @param stratumGroup a String that stands for the stratum group.
	 * @return a String
	 * @throws MetaModelException if the model has not been fitted or if the stratum group is not found in the Map.
	 */
	public String getSelectedOutputType(String stratumGroup) throws MetaModelException {
		MetaModel metaModel = getFittedMetaModel(stratumGroup);
		return metaModel.getSelectedOutputType();
	}
	
	/**
	 * Provide the possible output types (e.g. "Coniferous", "Broadleaved") for a particular stratum group.
	 * @param stratumGroup a String that stands for the stratum group.
	 * @return a List of String
	 * @throws MetaModelException if the model has not been fitted or if the stratum group is not found in the Map.
	 */
	public List<String> getPossibleOutputTypes(String stratumGroup) throws MetaModelException {
		if (!containsKey(stratumGroup)) {
			throw new MetaModelException("The meta model for this stratum group does not exist: " + stratumGroup);
		}
		MetaModel metaModel = get(stratumGroup);
		return metaModel.getPossibleOutputTypes();
	}


	@Override
	public void save(String filename) throws IOException {
		XmlSerializer serializer = new XmlSerializer(filename);
		serializer.writeObject(this);
	}

	@Override
	public void load(String filename) throws IOException {
		XmlDeserializer deserializer = new XmlDeserializer(filename);
		MetaModelManager manager = (MetaModelManager) deserializer.readObject();
		clear(); // we clear only after loading the new manager
		putAll(manager);
	}

	/**
	 * Load an instance of meta model from file and add it to the meta model manager.
	 * @param stratumGroup the name of the stratum group
	 * @param filename the path to the file to be loaded
	 * @throws IOException if an I/O error has occurred
	 */
	public void loadMetaModel(String stratumGroup, String filename) throws IOException {
		if (stratumGroup == null) {
			throw new InvalidParameterException("The stratum group cannot be null!");
		}
    	String newFilename = repicea.simulation.metamodel.MetaModel.getLightVersionFilename(filename);
    	if (!new File(newFilename).exists()) {	// then we convert the original version of the meta model into a light version for later deserialization
    		MetaModel.convertToLightVersion(filename);
    	}
		MetaModel metaModel = MetaModel.Load(newFilename);
		put(stratumGroup, metaModel);
	}
	
 	
}
