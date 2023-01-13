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

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import repicea.app.JSONConfigurationGlobal;
import repicea.app.REpiceaJSONConfiguration;
import repicea.io.Loadable;
import repicea.io.Saveable;
import repicea.serial.xml.XmlDeserializer;
import repicea.serial.xml.XmlSerializer;
import repicea.serial.xml.XmlSerializerChangeMonitor;
import repicea.simulation.metamodel.MetaModel.ModelImplEnum;
import repicea.stats.data.DataSet;

/**
 * Handle different instances of ExtMetaModel. It is thread safe.
 * @author Mathieu Fortin - December 2020
 */
public class MetaModelManager extends ConcurrentHashMap<String, MetaModel> implements Loadable, Saveable {	
	
	static {
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelManager", "repicea.simulation.metamodel.MetaModelManager");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel", "repicea.simulation.metamodel.MetaModel");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$Bound", "repicea.simulation.metamodel.MetaModel$Bound");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$InnerModel", "repicea.simulation.metamodel.MetaModel$InnerModel");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.ExtScriptResult", "repicea.simulation.metamodel.ScriptResult");				
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.DataBlockWrapper", "repicea.simulation.metamodel.DataBlockWrapper");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelGibbsSample", "repicea.simulation.metamodel.MetaModelGibbsSample");
	}
	
	
	protected static String LoggerName = MetaModelManager.class.getName();

	/**
	 * Constructor.
	 */
	public MetaModelManager() {}	

	
	/**
	 * Add a DataSet instance to a particular stratum group. 
	 * @param stratumGroup a String that stands for the stratum group
	 * @param initialAgeYr the age of the stratum at the beginning of the simulation
	 * @param result an ExtScriptResult instance that contains the simulation results
	 * @throws MetaModelException 
	 */
	public void addDataset(String stratumGroup, int initialAgeYr, ScriptResult result) throws MetaModelException {		
		if (!containsKey(stratumGroup)) {
			throw new MetaModelException("No metamodel exists for the stratum group name " + stratumGroup);
		}
		MetaModel metaModel = get(stratumGroup);
		metaModel.add(initialAgeYr, result);		
	}
	
	/**
	 * Add a DataSet instance to a particular stratum group. 
	 * @param stratumGroup a String that stands for the stratum group
	 * @param geoDomain the geographic domain of the source data
	 * @param dataSource the origin of the source data (ex: fourth inventory)
	 * @throws MetaModelException 
	 */
	public void createMetaModel(String stratumGroup, String geoDomain, String dataSource) throws MetaModelException {		
		if (containsKey(stratumGroup)) {
			throw new MetaModelException("A metamodel already exists for the stratum group name " + stratumGroup);
		}	
		
		put(stratumGroup, new MetaModel(stratumGroup, geoDomain, dataSource));
	}
	
	/**
	 * Fit all the meta-models.
	 * @param outputType the output type to which the model is to be fitted
	 * @param modImpl an implementation for the meta-model.
	 * @throws an ExtMetaModelException if one of the models has not converged.
	 */
	public void fitMetaModels(String outputType, ModelImplEnum modImpl) throws MetaModelException {
		this.fitMetaModels(keySet(), outputType, modImpl);
		for (String stratumGroup : keySet()) {
			MetaModel metaModel = get(stratumGroup);
			if (!metaModel.hasConverged()) {
				throw new MetaModelException("The meta-model for this stratum group has not converged: " + stratumGroup);
			}
		}
	}

	/**
	 * Fit all the meta-models using the default implementation 
	 * "Richards-Chapman including random effects".
	 * @param outputType the output type to which the model is to be fitted
	 * @throws an ExtMetaModelException if one of the models has not converged.
	 */
	public void fitMetaModels(String outputType) throws MetaModelException {
		fitMetaModels(outputType, ModelImplEnum.ChapmanRichardsWithRandomEffect);
	}

	/**
	 * Fit the meta-models identified in the stratumGroups argument using the default model
	 * implementation "Richards-Chapman including random effects".
	 * @param stratumGroups a Collection of stratum group ids
	 * @param outputType the output type to which the model is to be fitted
	 * @throws an ExtMetaModelException if one of the models has not converged.
	 */
	public void fitMetaModels(Collection<String> stratumGroups, String outputType) throws MetaModelException {
		fitMetaModels(stratumGroups, outputType, ModelImplEnum.ChapmanRichardsWithRandomEffect);
	}
	
	/**
	 * Fit the meta-models identified in the stratumGroups argument.
	 * @param stratumGroups a Collection of stratum group ids
	 * @param outputType the output type to which the model is to be fitted
	 * @param modImpl an implementation for the meta-model.
	 * @throws an ExtMetaModelException if one of the models has not converged.
	 */
	public void fitMetaModels(Collection<String> stratumGroups, String outputType, ModelImplEnum modImpl) throws MetaModelException {
		
		int NbWorkers = (int)((long) JSONConfigurationGlobal.getInstance().get(REpiceaJSONConfiguration.processingMaxThreads, 2L));
		
		// there is no point in creating more worker threads than there are stratumGroups, so limit their number if needed
		NbWorkers = NbWorkers > stratumGroups.size() ? stratumGroups.size() : NbWorkers; 
		
		try {
			LinkedBlockingQueue queue = new LinkedBlockingQueue();
			List<MetaModelManagerWorker> workers = new ArrayList<MetaModelManagerWorker>();
			for (int i = 0; i < NbWorkers; i++) {
				workers.add(new MetaModelManagerWorker(i, queue, outputType, modImpl));
			}
			
			for (String stratumGroup : stratumGroups) {
				MetaModel metaModel = get(stratumGroup);
				queue.add(metaModel);
			}
			
			for (int i = 0; i < NbWorkers; i++) {
				queue.add(MetaModelManagerWorker.FinishToken);
			}
			for (MetaModelManagerWorker t : workers) {
				t.join();
			}
		} catch (InterruptedException e) {
			throw new MetaModelException(e.getMessage());
		}
	}
	
	/**
	 * Compute and return the prediction generated from a particular meta-model.
	 * @param stratumGroup a String that stands for the stratum group
	 * @param ageYr the age of the stratum (yr) 
	 * @param timeSinceInitialDateYr the time since the initial date (yr)
	 * @param outputType a String e.g. Broadleaved, Coniferous
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
	 * Provide the stratum groups 
	 * @param stratumGroup a String that stands for the stratum group.
	 * @return a List of String (sorted)
	 * 
	 */
	public List<String> getStratumGroups() throws MetaModelException {
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
	 * Save a particular instance of meta model to file.
	 * @param stratumGroup
	 * @param filename
	 * @throws IOException
	 */
	public void saveMetaModel(String stratumGroup, String filename) throws IOException {
		if (containsKey(stratumGroup)) {
			MetaModel metaModel = get(stratumGroup);
			metaModel.save(filename);
		} else {
			throw new InvalidParameterException("This stratum group is not recognized: " + stratumGroup);
		}
	}
	
	/**
	 * Load an instance of meta model from file and add it to the meta model manager.
	 * @param stratumGroup
	 * @param filename
	 * @throws IOException
	 */
	public void loadMetaModel(String stratumGroup, String filename) throws IOException {
		if (stratumGroup == null) {
			throw new InvalidParameterException("The stratum group cannot be null!");
		}
		MetaModel metaModel = MetaModel.Load(filename);
		put(stratumGroup, metaModel);
	}
	
	
	
	
	/**
	 * Provide a final data set including the prediction of the meta model. 
	 * @param stratumGroup the stratum group
	 * @return a DataSet instance
	 * @throws MetaModelException if the stratum group does not exist or the model has not been fitted or has not converged yet.
	 */
	public DataSet getMetaModelResult(String stratumGroup) throws MetaModelException {
		if (containsKey(stratumGroup)) {
			MetaModel model = get(stratumGroup);
			if (model.hasConverged()) {
				return model.getFinalDataSet();
			} else {
				throw new MetaModelException("The model of this group : " + stratumGroup + " has not been fitted or has not converged yet!");
			}
		} else {
			throw new MetaModelException("This stratum group is not recognized: " + stratumGroup);
		}
	}
 	
}
