/*
 * This file is part of the repicea-metamodels library.
 *
 * Copyright (C) 2021-2024 His Majesty the King in right of Canada
 * Author: Mathieu Fortin, Canadian Forest Service
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.security.InvalidParameterException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.JsonWriter;
import com.cedarsoftware.io.WriteOptionsBuilder;

import repicea.io.FileUtility;
import repicea.io.Saveable;
import repicea.math.Matrix;
import repicea.math.SymmetricMatrix;
import repicea.serial.PostUnmarshalling;
import repicea.serial.SerializerChangeMonitor;
import repicea.serial.xml.XmlDeserializer;
import repicea.serial.xml.XmlSerializer;
import repicea.simulation.metamodel.ParametersMapUtilities.InputParametersMapKey;
import repicea.simulation.scriptapi.ScriptResult;
import repicea.stats.StatisticalUtility;
import repicea.stats.data.DataSet;
import repicea.stats.data.Observation;
import repicea.stats.data.StatisticalDataException;
import repicea.stats.estimates.GaussianEstimate;
import repicea.stats.estimators.mcmc.MetropolisHastingsParameters;
import repicea.util.REpiceaLogManager;


/**
 * A class that handles the data set and fits the meta-model for a group of strata.
 * 
 * @author Mathieu Fortin - December 2020
 */
public class MetaModel implements Saveable, PostUnmarshalling {
		
	static {
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.mcmc.MetropolisHastingsParameters",
				"repicea.stats.estimators.mcmc.MetropolisHastingsParameters");
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.mcmc.MetropolisHastingsAlgorithm",
				"repicea.stats.estimators.mcmc.MetropolisHastingsAlgorithm");
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.mcmc.MetropolisHastingsPriorHandler",
				"repicea.stats.estimators.mcmc.MetropolisHastingsPriorHandler");
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.mcmc.MetropolisHastingsSample",
				"repicea.stats.estimators.mcmc.MetropolisHastingsSample");
		SerializerChangeMonitor.registerClassNameChange("repicea.simulation.metamodel.ScriptResult",
				"repicea.simulation.scriptapi.ScriptResult");
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.GaussHermiteQuadrature", 
				"repicea.math.integral.GaussHermiteQuadrature");
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.AbstractGaussQuadrature", 
				"repicea.math.integral.AbstractGaussQuadrature");
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.AbstractGaussQuadrature$NumberOfPoints", 
				"repicea.math.integral.AbstractGaussQuadrature$NumberOfPoints");
	}

	static final String STRATUM_AGE_STR = "StratumAgeYr";

	public class MetaDataHelper {

		public MetaDataHelper() {}

		public MetaModelMetaData generate() {

			MetaModelMetaData data = new MetaModelMetaData();

			data.growth.geoDomain = MetaModel.this.geoDomain;
			data.growth.dataSource = MetaModel.this.dataSource;

			if (!MetaModel.this.scriptResults.isEmpty()) {						

				List<Integer> srKeys = new ArrayList<Integer>(MetaModel.this.scriptResults.keySet());

				Collections.sort(srKeys);

				boolean firstElement = true;
				for (Integer key : srKeys) {
					ScriptResult result = MetaModel.this.scriptResults.get(key);
					if (firstElement) {
						// fill in data that is constant 	
						data.growth.nbRealizations = result.getNbRealizations();
						data.growth.climateChangeOption = result.getClimateChangeScenario();
						data.growth.growthModel = result.getGrowthModel();							
					}

					// DateYrFieldName && upscaling
					List<Integer> temp = new ArrayList<Integer>();					 
					for (int i = 0; i < result.getDataSet().getNumberOfObservations(); i++) {
						if (i == 0)
							data.growth.upscaling.put(key, (String)result.getDataSet().getValueAt(i, ScriptResult.VarianceEstimatorType));

						Integer value = (Integer)result.getDataSet().getValueAt(i, ScriptResult.DateYrFieldName); 
						if (!temp.contains(value))
							temp.add(value);							
					}
					data.growth.dataSourceYears.put(key, temp);

					// nbPlots
					data.growth.nbPlots.put(key, result.getNbPlots());													
				}
			}

			
			data.fit.timeStamp = MetaModel.this.lastFitTimeStamp;
			data.fit.outputType = MetaModel.this.model.getSelectedOutputType();
			data.fit.fitModel = MetaModel.this.model.getModelImplementation().toString();
			data.fit.stratumGroup = MetaModel.this.stratumGroup;			

			return data;
		}
	}	
		
	
	static {
		SerializerChangeMonitor.registerClassNameChange(
				"repicea.simulation.metamodel.MetaModel$InnerModel",
				"repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation");
		SerializerChangeMonitor.registerClassNameChange(
				"repicea.simulation.metamodel.DataBlockWrapper",
				"repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation$DataBlockWrapper");
	}

	/**
	 * An enum constant that stands for the meta-model implementation.<p>
	 * Current implementations are:
	 * <ul>
	 * <li> ChapmanRichards
	 * <li> ChapmanRichardsWithRandomEffect
	 * <li> ChapmanRichardsDerivative
	 * <li> ChapmanRichardsDerivativeWithRandomEffect
	 * <li> Exponential
	 * <li> ExponentialWithRandomEffect
	 * <li> ModifiedChapmanRichardsDerivative
	 * <li> ModifiedChapmanRichardsDerivativeWithRandomEffect
	 * </ul>
	 */
	public static enum ModelImplEnum {
		ChapmanRichards, 
		ChapmanRichardsWithRandomEffect, 
		ChapmanRichardsDerivative,
		ChapmanRichardsDerivativeWithRandomEffect,
		Exponential,
		ExponentialWithRandomEffect,
		ModifiedChapmanRichardsDerivative,
		ModifiedChapmanRichardsDerivativeWithRandomEffect;
	}

	public enum PredictionVarianceOutputType {
		/**
		 * No variance output
		 */ 
		NONE,			
		/**
		 * Parameter estimates variance only
		 */
		PARAMEST,
		/**
		 * Parameter estimates variance including random effects
		 */
		PARAMESTRE,
	}

	
	
	protected MetropolisHastingsParameters mhSimParms;
	protected final Map<Integer, ScriptResult> scriptResults;
	protected AbstractModelImplementation model;
	private final String stratumGroup;
	private DataSet modelComparison;
	protected final String geoDomain;
	protected final String dataSource;
	public Date lastFitTimeStamp;
	private transient GaussianEstimate parameterEstimateGenerator;
	public static final String PREDICTIONS = "predictions";
	public static final String PREDICTION_VARIANCE = "predictionVariance";
	private transient LocalDateTime lastAccessed;	// the last datetime at which this metamodel was accessed (used in cache management in CFSStandGrowth) 
	


	/**
	 * Constructor.
	 * @param stratumGroup a String representing the stratum group
	 * @param geoDomain a String instance referring to the geographical area
	 * @param dataSource a String instance referring to the source of data 
	 */
	public MetaModel(String stratumGroup, String geoDomain, String dataSource) {
		this.stratumGroup = stratumGroup;
		this.geoDomain = geoDomain;
		this.dataSource = dataSource;
		
		scriptResults = new ConcurrentHashMap<Integer, ScriptResult>();
		setDefaultSettings();
	}

	

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Map<String, Object>[] convertJSONStrToLinkedHashMapArray(String jsonLinkedHashMap) {
//		Object o = JsonReader.jsonToJava(jsonLinkedHashMap, null);
		Object o = JsonIo.toObjects(jsonLinkedHashMap, null, LinkedHashMap.class);
		if (!o.getClass().isArray()) {
			throw new InvalidParameterException("The JSON string should stand for an array of LinkedHashMap instances!");
		}
		int length = Array.getLength(o);
		Map<String, Object>[] mapArray = new Map[length];
		for (int i = 0; i < length; i++) {
			Object innerMap = Array.get(o, i);
			if (!(innerMap instanceof Map)) {
				throw new InvalidParameterException("The JSON string should stand for an array of Map instances!");
			}
			mapArray[i] = (Map) innerMap;
		}
		return mapArray;
	}
	
	private void setDefaultSettings() {
		mhSimParms = new MetropolisHastingsParameters();
	}

	/**
	 * Provide the stratum group for this mate-model.
	 * 
	 * @return a String
	 */
	public String getStratumGroup() {
		return stratumGroup;
	}

	/**
	 * Return the state of the model.<p>
	 * The model can be used if it has converged.
	 * @return a boolean true if it has converged
	 */
	public boolean hasConverged() {
		return model != null ? model.hasConverged() : false;
	}
	
	/**
	 * Returns the timestamp of the last prediction from this metamodel
	 * 
	 * @return a valid Date or null if never accessed
	 */
	public LocalDateTime getLastAccessed() {
		return lastAccessed;
	}

	/**
	 * Add the result of a ScriptResult instance into the MetaModel instance
	 * @param initialAge the age of the stratum
	 * @param result a ScriptResult instance containing the stratum projection from the original 
	 */
	public void addScriptResult(int initialAge, ScriptResult result) {
		addStratumAgeFieldToInnerDataSet(result.getDataSet(), initialAge);
		boolean canBeAdded;
		if (scriptResults.isEmpty()) {
			canBeAdded = true;
		} else {
			ScriptResult previousResult = scriptResults.values().iterator().next();
			if (previousResult.isCompatible(result)) {
				canBeAdded = true;
			} else {
				canBeAdded = false;
			}
		}
		if (canBeAdded) {
			scriptResults.put(initialAge, result);
			model = null; // so that convergence is set to false by default	
		} else {
			throw new InvalidParameterException("The result parameter is not compatible with previous results in the map!");
		}
	}

	private AbstractModelImplementation getInnerModel(String outputType, ModelImplEnum modelImplEnum, Map<String, Object>[] startingValues)
			throws StatisticalDataException {
		AbstractModelImplementation model;
		switch (modelImplEnum) {
		case ChapmanRichards:
			model = new ChapmanRichardsModelImplementation(outputType, this, startingValues);
			break;
		case ChapmanRichardsWithRandomEffect:
			model = new ChapmanRichardsModelWithRandomEffectImplementation(outputType, this, startingValues);
			break;
		case ChapmanRichardsDerivative:
			model = new ChapmanRichardsDerivativeModelImplementation(outputType, this, startingValues);
			break;
		case ChapmanRichardsDerivativeWithRandomEffect:
			model = new ChapmanRichardsDerivativeModelWithRandomEffectImplementation(outputType, this, startingValues);
			break;
		case Exponential:
			model = new ExponentialModelImplementation(outputType, this, startingValues);
			break;
		case ExponentialWithRandomEffect:
			model = new ExponentialModelWithRandomEffectImplementation(outputType, this, startingValues);
			break;
		case ModifiedChapmanRichardsDerivative:
			model = new ModifiedChapmanRichardsDerivativeModelImplementation(outputType, this, startingValues);
			break;
		case ModifiedChapmanRichardsDerivativeWithRandomEffect:
			model = new ModifiedChapmanRichardsDerivativeModelWithRandomEffectImplementation(outputType, this, startingValues);
			break;
		default:
			throw new InvalidParameterException("This ModelImplEnum " + modelImplEnum.name() + " has not been implemented yet!");
		}
		return model;
	}

	/**
	 * Provide the possible output types.<p>
	 * Those are retrieved from the ScriptResult instances.
	 * @return a List of String
	 */
	public List<String> getPossibleOutputTypes() {
		return getPossibleOutputTypes(scriptResults);
	}

	protected static List<String> getPossibleOutputTypes(Map<Integer, ScriptResult> scriptResults) {
		List<String> possibleOutputTypes = new ArrayList<String>();
		if (!scriptResults.isEmpty()) {
			ScriptResult scriptRes = scriptResults.values().iterator().next();
			possibleOutputTypes.addAll(scriptRes.getOutputTypes());
		}
		return possibleOutputTypes;
	}

	static class InnerWorker extends Thread implements Comparable<InnerWorker> {

		final AbstractModelImplementation ami;

		InnerWorker(AbstractModelImplementation ami) {
			super(ami);
			this.ami = ami;
			setName(ami.getModelImplementation().name());
		}

		@Override
		public int compareTo(InnerWorker o) {
			if (ami.mh.getLogPseudomarginalLikelihood() > o.ami.mh.getLogPseudomarginalLikelihood()) {
				return -1;
			} else if (ami.mh.getLogPseudomarginalLikelihood() == o.ami.mh.getLogPseudomarginalLikelihood()) {
				return 0;
			} else {
				return 1;
			}
		}
	}

	private InnerWorker performModelSelection(List<InnerWorker> innerWorkers) {
		List<InnerWorker> newList = new ArrayList<InnerWorker>();
		List<InnerWorker> didNotConvergeList = new ArrayList<InnerWorker>();
		DataSet d = new DataSet(Arrays.asList(new String[] { "ModelImplementation", "LPML"}));
		for (InnerWorker w : innerWorkers) {
			if (w.ami.hasConverged()) {
				newList.add(w);
			} else {
				didNotConvergeList.add(w);
			}
		}
		Collections.sort(newList);
		for (InnerWorker w : newList) {
			d.addObservation(new Object[] { w.ami.getModelImplementation().name(),
					w.ami.mh.getLogPseudomarginalLikelihood()});
		}
		for (InnerWorker w : didNotConvergeList) {
			d.addObservation(new Object[] { w.ami.getModelImplementation().name(),
					Double.NaN});
		}
		modelComparison = d;
		return newList.get(0);
	}

	@SuppressWarnings({ "unchecked"})
	private static LinkedHashMap<ModelImplEnum, Map<String, Object>[]> formatModelImplementationMap(Map<String, Object> modelImplementations) {
		LinkedHashMap<ModelImplEnum, Map<String, Object>[]> myImplementations = new LinkedHashMap<ModelImplEnum, Map<String, Object>[]>();
		for (String impl : modelImplementations.keySet()) {
			ModelImplEnum myImpl = null;
			try { 
				myImpl = ModelImplEnum.valueOf(impl);
			} catch (Exception e) {
				throw new InvalidParameterException("The model implementation " + impl + " is not recognized!");
			}
			Object value = modelImplementations.get(impl);
			if (value == null) {
				myImplementations.put(myImpl, null);
			} else {
				if (value.getClass().isArray()) {
					int length = Array.getLength(value);
					if (length == 0) {
						throw new InvalidParameterException("The starting values of the parameters of the implementation " + myImpl + " are empty!");
					} else {
						Object arrayValue = Array.get(value, 0);
						if (arrayValue instanceof LinkedHashMap) {
							myImplementations.put(myImpl, (LinkedHashMap[]) value);
						} else {
							throw new InvalidParameterException("The starting values should be an array of LinkedHashMap instances!");
						}
					}
				} else if (value instanceof String) {
					Map<String,Object>[] startingValues = MetaModel.convertJSONStrToLinkedHashMapArray((String) value);
					myImplementations.put(myImpl, startingValues);
				} else {
					throw new InvalidParameterException("The type of object in the values of the modelImplementations map should be arrays of LinkedHashMap instances or a JSON representation of this array!");
				}
			}
		}
		return myImplementations;
	}
	
	
	/**
	 * Fit the meta-model.<p>
	 * 
	 * @param outputType the output type the model will be fitted to (e.g., volumeAlive_Coniferous)
	 * @param modelImplementations a Map of List of strings that are the names of the ModelImplEnum constants
	 * @return a string either DONE or ERROR: [...] if something went wrong
	 * @see ModelImplEnum
	 */
	public String fitModel(String outputType, LinkedHashMap<String, Object> modelImplementations) {
		if (outputType == null || !getPossibleOutputTypes().contains(outputType)) {
			throw new InvalidParameterException("The outputType argument should be one of the possible output type (see the getPossibleOutputTypes() method)!");
		}
		if (modelImplementations == null || modelImplementations.isEmpty()) {
			throw new InvalidParameterException("The modelImplementationStrings argument should be a Map!");
		}
		
		LinkedHashMap<ModelImplEnum, Map<String, Object>[]> formattedModelImplementations = formatModelImplementationMap(modelImplementations);
		
		model = null; // reset the convergence to false
		
		REpiceaLogManager.logMessage(MetaModelManager.LoggerName, Level.INFO, "Meta-model " + stratumGroup, "----------- Modeling output type: " + outputType + " ----------------");
		try {
			List<InnerWorker> modelList = new ArrayList<InnerWorker>();

			for (ModelImplEnum e : formattedModelImplementations.keySet()) {
				Map<String, Object>[] startingValues = formattedModelImplementations.get(e);
				InnerWorker w = new InnerWorker(getInnerModel(outputType, e, startingValues));
				w.start();
				modelList.add(w);
			}
			for (InnerWorker w : modelList) {
				w.join();
			}
			InnerWorker selectedWorker = performModelSelection(modelList);
			REpiceaLogManager.logMessage(MetaModelManager.LoggerName, Level.INFO, "Meta-model " + stratumGroup,
					"Selected model is " + selectedWorker.ami.getModelImplementation().name());
			model = selectedWorker.ami;
			
			lastFitTimeStamp = new Date(System.currentTimeMillis());
			return "DONE";
		} catch (Exception e1) {
			e1.printStackTrace();
			return "ERROR: " + e1.getMessage();
		}
	}

	
	
	/**
	 * Provide a single prediction using the model parameters. <p>
	 * 
	 * The method automatically implements the regeneration lag. 
	 * 
	 * @param ageYr The ageYr for which the prediction is to be computed                   
	 * @param timeSinceInitialDateYr The number of years since initial date year for the prediction
	 * @param randomEffect the value of the random effect
	 * @param parameterEstimates a Matrix instance. If null, the final parameter estimates are used.
	 * @return the prediction
	 * @throws MetaModelException if the meta-model has not converged
	 */
	double getPrediction(double ageYr, int timeSinceInitialDateYr, double randomEffect, Matrix parameterEstimates) throws MetaModelException {
		if (hasConverged()) {
			double ageYrMod = ageYr - model.getRegenerationLagYrIfAny();
			double pred = ageYrMod <= 0d ?
					0d :
						model.getPrediction(ageYrMod, timeSinceInitialDateYr, randomEffect, parameterEstimates);
			lastAccessed = LocalDateTime.now();
			return pred;
		} else {
			throw new MetaModelException("The meta-model has not converged or has not been fitted yet!");
		}
	}
	
	private GaussianEstimate getParameterEstimateGenerator() {
		if (parameterEstimateGenerator == null)
			parameterEstimateGenerator = new GaussianEstimate(
					model.getParameters().getSubMatrix(model.fixedEffectsParameterIndices, null),
					SymmetricMatrix.convertToSymmetricIfPossible(model.getParmsVarCov().getSubMatrix(model.fixedEffectsParameterIndices, model.fixedEffectsParameterIndices)));
		return parameterEstimateGenerator;
	}
	
	
	/**
	 * Provide deterministic predictions and associated variance using the model parameters.<p>
	 * 
	 * The regeneration lag is accounted for in the predictions.
	 * 
	 * @param ageYr An array of all ageYrs for which the predictions are to be computed                   
	 * @param timeSinceInitialDateYr The number of years since initial date year for the predictions
	 * @param varianceOutputType The desired variance output type.  
	 * 			NONE means no variance output for predictions 
	 * 			PARAMEST returns simple variance for parameter estimates
	 * 			PARAMESTRE returns variance for parameter estimates including random effect on variance
	 * 			 
	 * @return the predictions in a DataSet instance which has three columns (AgeYr, Pred, Variance). If the varianceOutputType 
	 * is set to NONE, the DataSet instance does not include the last column.
	 * @throws MetaModelException if the meta-model has not converged
	 */
	public DataSet getPredictions(int[] ageYr, int timeSinceInitialDateYr, PredictionVarianceOutputType varianceOutputType) throws MetaModelException {
		boolean includeVariance = varianceOutputType != PredictionVarianceOutputType.NONE; 
		DataSet ds = includeVariance ? 
				new DataSet(Arrays.asList(new String[] {"AgeYr", "Pred", "Variance"})) :
					new DataSet(Arrays.asList(new String[] {"AgeYr", "Pred"}));
		for (int k = 0; k < ageYr.length; k++) {
			double pred = getPrediction(ageYr[k], timeSinceInitialDateYr, 0d, null);
			if (includeVariance) {
				double variance  = getPredictionVariance(ageYr[k], timeSinceInitialDateYr, varianceOutputType == PredictionVarianceOutputType.PARAMESTRE);
				ds.addObservation(new Object[] {ageYr[k], pred, variance});
			} else {
				ds.addObservation(new Object[] {ageYr[k], pred});
			}
		}
		return ds;
	}
	
	
	/**
	 * Provide multiple predictions sets using the model parameters using Monte Carlo simulation on model parameters.
	 * 
	 * @param ageYr An array of all ageYrs for which the predictions are to be computed                   
	 * @param timeSinceInitialDateYr The number of years since initial date year for the predictions
	 * @param nbSubjects The number of subjects to generate random parameters for  (use 0 to disable MC simulation) 
	 * @param nbRealizations The number of realizations to generate random parameters for (use 0 to disable MC simulation)
	 * 			 
	 * @return a DataSet with four columns (RealizationID, SubjectID, AgeYr, Pred)
	 * @throws MetaModelException if the meta-model has not converged
	 */	
	public DataSet getMonteCarloPredictions(int[] ageYr, int timeSinceInitialDateYr, int nbSubjects, int nbRealizations) throws MetaModelException {
		if (hasConverged()) {
			boolean randomEffectVariabilityEnabled = nbSubjects > 0;
			boolean parameterVariabilityEnabled = nbRealizations > 0;
			
			List<Matrix> parmDeviates = new ArrayList<Matrix>();
			for (int i = 0; i < nbRealizations; i++) {
				parmDeviates.add(getParameterEstimateGenerator().getRandomDeviate());
			}
			
			double randomEffectStd = model instanceof AbstractMixedModelFullImplementation ? 
					model.getParameters().getValueAt(
							model.parameterIndexMap.get(AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD),
							0) : 
						0d;
					
			int ns = randomEffectVariabilityEnabled ? nbSubjects : 1;
			int nr = parameterVariabilityEnabled ? nbRealizations : 1;
			DataSet ds = new DataSet(Arrays.asList(new String[] {"RealizationID", "SubjectID", "AgeYr", "Pred"}));
			
			for (int i = 0; i < nr; i++) {
				for (int j = 0; j < ns; j++) {
					double rj = randomEffectVariabilityEnabled ? 
							StatisticalUtility.getRandom().nextGaussian() * randomEffectStd :
								0d;
					for (int k = 0; k < ageYr.length; k++) {						
						double pred = getPrediction(ageYr[k], timeSinceInitialDateYr, rj, parameterVariabilityEnabled ? parmDeviates.get(i) : getFinalParameterEstimates());
						ds.addObservation(new Object[] {i, j , ageYr[k], pred});
					}
				}
			}
			
			lastAccessed = LocalDateTime.now();
						
			return ds;
		} else {
			throw new MetaModelException("The meta-model has not converged or has not been fitted yet!");
		}
	}
	
	/**
 	 * Provide the prediction variance.
 	 * 
 	 * The method automatically implements the regeneration lag. 
 	 *
	 * @param ageYr stratum age (yr) 
	 * @param timeSinceInitialDateYr The number of years since initial date year for the predictions
	 * @param includeRandomEffectVariance a boolean to include the random effect
	 * @return a double
	 * @throws MetaModelException if the model has not converged
	 */
	double getPredictionVariance(double ageYr, int timeSinceInitialDateYr, boolean includeRandomEffectVariance) throws MetaModelException {
		if (hasConverged()) {
			double ageYrMod = ageYr - model.getRegenerationLagYrIfAny();
			if (ageYrMod <= 0d) {
				return 0d;
			}
			double variance = model.getPredictionVariance(ageYrMod, timeSinceInitialDateYr, 0d);
			if (includeRandomEffectVariance && model instanceof AbstractMixedModelFullImplementation) {
				variance += ((AbstractMixedModelFullImplementation)model).getVarianceDueToRandomEffect(ageYr, timeSinceInitialDateYr);
			}
			return variance;
		} else {
			throw new MetaModelException("The meta-model has not converged or has not been fitted yet!");
		}
	}

	protected Matrix getFinalParameterEstimates() {
		return model.getParameters();
	}

	/**
	 * Export a final dataset, that is the initial data set plus the meta-model
	 * predictions.<p>
	 * This works only if the model has converged.
	 * 
	 * @param filename the name of the file
	 * @throws IOException if an I/O error has occurred
	 * @throws MetaModelException if the model has not converged
	 */
	public void exportFinalDataSet(String filename) throws IOException, MetaModelException {
		getFinalDataSet().save(filename);
	}

	/**
	 * Provide the selected output type that was set in the call to method fitModel.
	 * 
	 * @return a String
	 */
	public String getSelectedOutputType() {
		return model != null ? model.getSelectedOutputType() : "";
	}

	/**
	 * Save a CSV file containing the final sequence produced by the
	 * Metropolis-Hastings algorithm. <p>
	 * This sequence does not include burn-in period and only every nth observation 
	 * from the sequence is kept in this final sequence. The number of burn-in samples 
	 * to be dropped is set by the nbBurnIn member while every nth observation is set 
	 * by the oneEach member.
	 * 
	 * @param filename the name of the file
	 * @throws IOException if an I/O error has occurred
	 */
	void exportMetropolisHastingsSample(String filename) throws IOException {
		model.mh.exportMetropolisHastingsSample(filename);
	}
	
	/**
	 * Call the same method on the MetropolisHastingsAlgorithm instance.
	 * @return a DataSet instance
	 * @see repicea.stats.estimators.mcmc.MetropolisHastingsAlgorithm#convertMetropolisHastingsSampleToDataSet()
	 */
	public DataSet convertMetropolisHastingsSampleToDataSet() {
		return model.mh.convertMetropolisHastingsSampleToDataSet();
	}

	/**
	 * Provide a DataSet instance with the observed values. 
	 * @return a DataSet instance
	 * @throws MetaModelException if the model has not converged
	 */
	public DataSet getFinalDataSet() throws MetaModelException {
		if (hasConverged()) {
			return model.getFinalDataSet();
		} else {
			throw new MetaModelException("The model of this group : " + stratumGroup + " has not been fitted or has not converged yet!");
		}
	}

	/**
	 * {@inheritDoc}<p>
	 * 
	 * If the model member is null, the model is saved as a proto model, that is a model that
	 * has not been fitted yet.
	 */
	@Override
	public void save(String filename) throws IOException {
		XmlSerializer serializer = new XmlSerializer(filename);
		serializer.writeObject(this);
		
		if (model != null) { // otherwise it remains a proto-model and there is no need for metadata
			MetaDataHelper helper = new MetaDataHelper(); 
			MetaModelMetaData data = helper.generate();
					
			FileOutputStream os = new FileOutputStream(FileUtility.replaceExtensionBy(filename, "json"));
			
//			Map<String, Object> options = new HashMap<String, Object>();
//			options.put(JsonWriter.PRETTY_PRINT, true);
//			options.put(JsonWriter.DATE_FORMAT, JsonWriter.ISO_DATE_TIME_FORMAT+"Z");
//			JsonWriter jw = new JsonWriter(os, options);
			
			WriteOptionsBuilder builder = new WriteOptionsBuilder();
			builder.prettyPrint(true);
			builder.dateTimeFormat(WriteOptionsBuilder.ISO_DATE_TIME_FORMAT+"Z");
			JsonWriter jw = new JsonWriter(os, builder.build());
			
			jw.write(data);	
			jw.close();
		}
	}

	/**
	 * Load a meta-model instance from file.
	 * 
	 * @param filename the path to the file
	 * @return a MetaModel instance
	 * @throws IOException if an I/O error has occurred
	 */
	public static MetaModel Load(String filename) throws IOException {
		XmlDeserializer deserializer = new XmlDeserializer(filename);
		Object obj = deserializer.readObject();
		MetaModel metaModel = (MetaModel) obj;
		if (metaModel.mhSimParms == null) { // saved under a former implementation where this variable was static
			metaModel.setDefaultSettings();
		}
		return metaModel;
	}

	/**
	 * Provide a summary of the model. 
	 * 
	 * @return the summary in a String instance or null if the model has not converged
	 */
	public String getSummary() {
		if (hasConverged()) {
			return model.getSummary();
		} else {
			System.out.println("The model has not been fitted yet!");
			return null;
		}
	}

	/**
	 * Provide the comparison between the different meta-models.
	 * @return a DataSet instance or null if the meta-model has not converged
	 */
	public DataSet getModelComparison() {
		if (hasConverged()) {
			return modelComparison;
		} else {
			System.out.println("The model has not been fitted yet!");
			return null;
		}
	}

	/**
	 * Check if the variance is available throughout the ScriptResult instances. <p>
	 * It returns false if the scriptResults map is empty or at least one ScriptResult instance does 
	 * not have its variance field.
	 * @return a boolean
	 */
	public boolean isVarianceAvailable() {
		if (scriptResults.isEmpty()) {
			return false;
		} else {
			for (ScriptResult sr : scriptResults.values()) {
				if (!sr.isVarianceAvailable()) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * Compile all the script results into a DataSet instance.
	 * @return a DataSet instance
	 */
	public DataSet convertScriptResultsIntoDataSet() {
		DataSet ds = null;
		for (Integer ageYr : scriptResults.keySet()) {
			ScriptResult sr = scriptResults.get(ageYr);
			DataSet innerDataSet = sr.getDataSet();
			if (ds == null) {
				ds = new DataSet(innerDataSet.getFieldNames());
			}
			for (Observation o : innerDataSet.getObservations()) {
				ds.addObservation(o.toArray());
			}
		}
		return ds;
	}
	
	/**
	 * Save a lighter version of a previously serialized meta-model. <p>
	 * This lighter version drops the final sample selection for a lighter deserialization.
	 * 
	 * @param filename the filename of the serialized instance
	 * @throws IOException if an I/O error has occurred
	 */
	public static void convertToLightVersion(String filename) throws IOException {
		MetaModel instance = Load(filename);
		instance.model.mh.releaseFinalSampleSelection();
		String newFilename = getLightVersionFilename(filename);
		XmlSerializer serializer = new XmlSerializer(newFilename);
		serializer.writeObject(instance);
	}

	/**
	 * Provide the filename for the light version of the MetaModel instance. <p>
	 * The suffix "_light" is inserted before the extension.
	 * 
	 * @param originalFilename the original filename.
	 * @return the filename for the light version
	 */
	public static String getLightVersionFilename(String originalFilename) {
		String newFilename = originalFilename.substring(0, originalFilename.lastIndexOf(".")) + "_light" + originalFilename.substring(originalFilename.lastIndexOf("."));
		return newFilename;
	}
	
	/**
	 * Accessor to the parameters of the Metropolis-Hastings algorithm.
	 * @return a MetropolisHastingsParameters instance
	 */
	public MetropolisHastingsParameters getMetropolisHastingsParameters() {
		return mhSimParms;
	}
	

	/**
	 * Convert parameters into a proper LinkedHashMap instance. <p>
	 * The parameters are expected to come in the order of the InputParametersMapKey enum.
	 * @param record an array of objects
	 * @return a LinkedHashMap instance
	 * @see InputParametersMapKey
	 */
	public static LinkedHashMap<String,Object> convertParameters(Object[] record) {
		if (record.length != InputParametersMapKey.values().length) {
			throw new InvalidParameterException("The length of the record is supposed to be " + InputParametersMapKey.values().length + "!");
		}
		LinkedHashMap<String,Object> oMap = new LinkedHashMap<String,Object>();
		for (InputParametersMapKey key : InputParametersMapKey.values()) {
			oMap.put(key.name(), record[key.ordinal()]);
		}
		return oMap;
	}

	private static void addStratumAgeFieldToInnerDataSet(DataSet innerDataset, int stratumAgeYr) {
		Object[] stratumAgeFieldValues = new Object[innerDataset.getNumberOfObservations()];
		for (int i = 0; i < stratumAgeFieldValues.length; i++) {
			stratumAgeFieldValues[i] = stratumAgeYr;
		}
		innerDataset.addField(STRATUM_AGE_STR, stratumAgeFieldValues);
	}
	
	@Override
	public void postUnmarshallingAction() {
		if (scriptResults != null) {
			for (Integer stratumAgeYr : scriptResults.keySet()) {
				ScriptResult sr = scriptResults.get(stratumAgeYr);
				DataSet innerDataset = sr.getDataSet();
				if (!innerDataset.getFieldNames().contains(STRATUM_AGE_STR)) {
					addStratumAgeFieldToInnerDataSet(innerDataset, stratumAgeYr);
				}
			}
		}
		if (model != null) {
			if (model.parameterIndexMap == null) {
				model.getParameterNames();	// this way we make sure the parameter index map is instantiated
			}
		}
	}
	
	/**
	 * Provide the average regeneration lag if there is one.
	 * @return the regeneration lag (yr) or 0 if there is no regeneration lag in the model.
	 * @throws MetaModelException if the model has not been fitted or has not converged
	 */
	public double getRegenerationLagYrIfAny() throws MetaModelException {
		if (hasConverged()) {
			return model.getRegenerationLagYrIfAny();
		} else {
			throw new MetaModelException("The model of this group : " + stratumGroup + " has not been fitted or has not converged yet!");
		}

	}
	
	
//	public static void main(String[] args) throws IOException {
//		
//		MetaModel.convertToLightVersion("C:\\Users\\matforti\\OneDrive - NRCan RNCan\\Documents\\7_Developpement\\ModellingProjects\\MetaModelSet\\incubator\\QC\\USGCTile184\\PSP\\Artemis2009\\QC_USGCTile184_Tile184_NoChange_AliveAboveGroundBiomass_AllSpecies.zml");
//		
//		MetaModel m = MetaModel.Load("C:\\Users\\matforti\\OneDrive - NRCan RNCan\\Documents\\7_Developpement\\ModellingProjects\\MetaModelSet\\incubator\\QC\\USGCTile184\\PSP\\Artemis2009\\QC_USGCTile184_Tile184_NoChange_AliveAboveGroundBiomass_AllSpecies.zml");
//		
//		m.scriptResults.remove(10);
//		m.save("C:\\Users\\matforti\\OneDrive - NRCan RNCan\\Documents\\7_Developpement\\ModellingProjects\\MetaModelSet\\proto\\QC\\3EST\\PET4\\Artemis2009\\QC_3EST_RS38_NoChange_NEW.zml");
//		int u = 0;
		
//	}
	
}
