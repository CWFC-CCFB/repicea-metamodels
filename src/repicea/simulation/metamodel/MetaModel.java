/*
 * This file is part of the repicea-metamodels library.
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

import java.io.FileOutputStream;
import java.io.IOException;
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

import com.cedarsoftware.util.io.JsonWriter;

import repicea.io.FileUtility;
import repicea.io.Saveable;
import repicea.math.Matrix;
import repicea.math.SymmetricMatrix;
import repicea.serial.xml.XmlDeserializer;
import repicea.serial.xml.XmlSerializer;
import repicea.serial.xml.XmlSerializerChangeMonitor;
import repicea.simulation.scriptapi.ScriptResult;
import repicea.stats.StatisticalUtility;
import repicea.stats.data.DataSet;
import repicea.stats.data.StatisticalDataException;
import repicea.stats.estimates.GaussianEstimate;
import repicea.stats.estimators.mcmc.MetropolisHastingsParameters;
import repicea.util.REpiceaLogManager;


/**
 * A class that handles the data set and fits the meta-model for a group of strata.
 * 
 * @author Mathieu Fortin - December 2020
 */
public class MetaModel implements Saveable {
		
	static {
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.stats.mcmc.MetropolisHastingsParameters",
				"repicea.stats.estimators.mcmc.MetropolisHastingsParameters");
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.stats.mcmc.MetropolisHastingsAlgorithm",
				"repicea.stats.estimators.mcmc.MetropolisHastingsAlgorithm");
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.stats.mcmc.MetropolisHastingsPriorHandler",
				"repicea.stats.estimators.mcmc.MetropolisHastingsPriorHandler");
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.stats.mcmc.MetropolisHastingsSample",
				"repicea.stats.estimators.mcmc.MetropolisHastingsSample");
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.simulation.metamodel.ScriptResult",
				"repicea.simulation.scriptapi.ScriptResult");
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.GaussHermiteQuadrature", 
				"repicea.math.integral.GaussHermiteQuadrature");
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.AbstractGaussQuadrature", 
				"repicea.math.integral.AbstractGaussQuadrature");
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.AbstractGaussQuadrature$NumberOfPoints", 
				"repicea.math.integral.AbstractGaussQuadrature$NumberOfPoints");
	}


	public class MetaDataHelper {

		public MetaDataHelper() {
		}

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
//						data.growth.climateChangeOption = ((Enum<?>)result.climateChangeScenario).name();
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
		repicea.serial.xml.XmlSerializerChangeMonitor.registerClassNameChange(
				"repicea.simulation.metamodel.MetaModel$InnerModel",
				"repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation");
		repicea.serial.xml.XmlSerializerChangeMonitor.registerClassNameChange(
				"repicea.simulation.metamodel.DataBlockWrapper",
				"repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation$DataBlockWrapper");
	}


	public static enum ModelImplEnum {
		ChapmanRichards(true), ChapmanRichardsWithRandomEffect(false), ChapmanRichardsDerivative(true),
		ChapmanRichardsDerivativeWithRandomEffect(false);

		private static List<ModelImplEnum> ModelsWithoutRandomEffects;
		private static Map<ModelImplEnum, ModelImplEnum> MatchingModelsWithRandomEffects;

		final boolean modelWithoutRandomEffect;

		ModelImplEnum(boolean modelWithoutRandomEffect) {
			this.modelWithoutRandomEffect = modelWithoutRandomEffect;
		}

		public static List<ModelImplEnum> getModelsWithoutRandomEffects() {
			if (ModelsWithoutRandomEffects == null) {
				ModelsWithoutRandomEffects = new ArrayList<ModelImplEnum>();
				for (ModelImplEnum e : ModelImplEnum.values()) {
					if (e.modelWithoutRandomEffect) {
						ModelsWithoutRandomEffects.add(e);
					}
				}
			}
			return ModelsWithoutRandomEffects;
		}

		private static Map<ModelImplEnum, ModelImplEnum> getMatchingModelsWithRandomEffects() {
			if (MatchingModelsWithRandomEffects == null) {
				MatchingModelsWithRandomEffects = new HashMap<ModelImplEnum, ModelImplEnum>();
				MatchingModelsWithRandomEffects.put(ChapmanRichards, ChapmanRichardsWithRandomEffect);
				MatchingModelsWithRandomEffects.put(ChapmanRichardsDerivative,
						ChapmanRichardsDerivativeWithRandomEffect);
			}
			return MatchingModelsWithRandomEffects;
		}

		public static ModelImplEnum getMatchingModelWithRandomEffects(ModelImplEnum modelImplEnum) {
			return getMatchingModelsWithRandomEffects().get(modelImplEnum);
		}

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

	private AbstractModelImplementation getInnerModel(String outputType, ModelImplEnum modelImplEnum)
			throws StatisticalDataException {
		AbstractModelImplementation model;
		switch (modelImplEnum) {
		case ChapmanRichards:
			model = new ChapmanRichardsModelImplementation(outputType, this);
			break;
		case ChapmanRichardsWithRandomEffect:
			model = new ChapmanRichardsModelWithRandomEffectImplementation(outputType, this);
			break;
		case ChapmanRichardsDerivative:
			model = new ChapmanRichardsDerivativeModelImplementation(outputType, this);
			break;
		case ChapmanRichardsDerivativeWithRandomEffect:
			model = new ChapmanRichardsDerivativeModelWithRandomEffectImplementation(outputType, this);
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
		double prob;

		InnerWorker(AbstractModelImplementation ami) {
			super(ami);
			this.ami = ami;
			setName(ami.getModelImplementation().name());
		}

		@Override
		public int compareTo(InnerWorker o) {
			if (prob > o.prob) {
				return -1;
			} else if (prob == o.prob) {
				return 0;
			} else {
				return 1;
			}
		}
	}

	private InnerWorker performModelSelection(List<InnerWorker> innerWorkers) {
		double sumProb = 0;
		List<InnerWorker> newList = new ArrayList<InnerWorker>();
		for (InnerWorker w : innerWorkers) {
			if (w.ami.hasConverged()) {
				newList.add(w);
				sumProb += Math.exp(w.ami.mh.getLogPseudomarginalLikelihood());
				REpiceaLogManager.logMessage(MetaModelManager.LoggerName, Level.INFO, "Meta-model " + stratumGroup,
						"Result for the implementation " + w.ami.getModelImplementation().name());
			}
		}
		DataSet d = new DataSet(Arrays.asList(new String[] { "ModelImplementation", "LPML", "Prob" }));
		for (InnerWorker w : newList) {
			w.prob = Math.exp(w.ami.mh.getLogPseudomarginalLikelihood()) / sumProb;
			d.addObservation(new Object[] { w.ami.getModelImplementation().name(),
					w.ami.mh.getLogPseudomarginalLikelihood(), w.prob });
//			System.out.println("Implementation " + w.ami.getModelImplementation().name() + ": " + w.prob);
		}
		modelComparison = d;
		Collections.sort(newList);
		return newList.get(0);
	}

	/**
	 * Fit the meta-model.
	 * 
	 * @param outputType the output type the model will be fitted to (e.g., volumeAlive_Coniferous)
	 * @param enableMixedModelImplementations true to test meta-models with stratum random effects
	 * @return a boolean true if the model has converged or false otherwise
	 */
	public boolean fitModel(String outputType, boolean enableMixedModelImplementations) {
		model = null; // reset the convergence to false
		REpiceaLogManager.logMessage(MetaModelManager.LoggerName, Level.INFO, "Meta-model " + stratumGroup,
				"----------- Modeling output type: " + outputType + " ----------------");
		try {
			List<InnerWorker> modelList = new ArrayList<InnerWorker>();

			List<ModelImplEnum> myImplementations = new ArrayList<ModelImplEnum>();
			myImplementations.add(ModelImplEnum.ChapmanRichards);
			if (enableMixedModelImplementations) {
				myImplementations.add(ModelImplEnum.ChapmanRichardsWithRandomEffect);
			}
			myImplementations.add(ModelImplEnum.ChapmanRichardsDerivative);
			if (enableMixedModelImplementations) {
				myImplementations.add(ModelImplEnum.ChapmanRichardsDerivativeWithRandomEffect);
			}
			for (ModelImplEnum e : myImplementations) { // use the basic models first, i.e. those without random effects
				InnerWorker w = new InnerWorker(getInnerModel(outputType, e));
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
//			System.out.println(model.getSummary());
			return true;
		} catch (Exception e1) {
			e1.printStackTrace();
			return false;
		}
	}

	/**
	 * Provide a single prediction using the model parameters
	 * 
	 * @param ageYr The ageYr for which the prediction is to be computed                   
	 * @param timeSinceInitialDateYr The number of years since initial date year for the prediction
	 * @return the prediction
	 * @throws MetaModelException if the meta-model has not converged
	 */
	public double getPrediction(int ageYr, int timeSinceInitialDateYr) throws MetaModelException {
		if (hasConverged()) {
			double pred = model.getPrediction(ageYr, timeSinceInitialDateYr, 0d);
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
	 * Provide multiple predictions and associated variance using the model parameters  
	 * 
	 * @param ageYr An array of all ageYrs for which the predictions are to be computed                   
	 * @param timeSinceInitialDateYr The number of years since initial date year for the predictions
	 * @param varianceOutputType The desired variance output type.  
	 * 			NONE means no variance output for predictions 
	 * 			PARAMEST returns simple variance for parameter estimates
	 * 			PARAMESTRE returns variance for parameter estimates including random effect on variance
	 * 			 
	 * @return the predictions two different maps : one for PREDICTIONS and one for PREDICTION_VARIANCE
	 * @throws MetaModelException if the meta-model has not converged
	 */
	public LinkedHashMap<String, LinkedHashMap<Integer, Double>> getPredictions(int[] ageYr, int timeSinceInitialDateYr, PredictionVarianceOutputType varianceOutputType) throws MetaModelException {
		LinkedHashMap<String, LinkedHashMap<Integer, Double>> result = new LinkedHashMap<String, LinkedHashMap<Integer, Double>>();
		result.put(PREDICTIONS, getMonteCarloPredictions(ageYr, timeSinceInitialDateYr, 0, 0).get(0).get(0));
		if (varianceOutputType != PredictionVarianceOutputType.NONE) {
			LinkedHashMap<Integer, Double> variance = new LinkedHashMap<Integer, Double>(ageYr.length);
			for (int k = 0; k < ageYr.length; k++) {
				variance.put(ageYr[k], getPredictionVariance(ageYr[k], timeSinceInitialDateYr, varianceOutputType == PredictionVarianceOutputType.PARAMESTRE));
			}
			
			result.put(PREDICTION_VARIANCE, variance);
		}
		
		return result;
	}
	
	/**
	 * Provide multiple predictions sets using the model parameters using Monte Carlo simulation on model parameters 
	 * 
	 * @param ageYr An array of all ageYrs for which the predictions are to be computed                   
	 * @param timeSinceInitialDateYr The number of years since initial date year for the predictions
	 * @param nbSubjects The number of subjects to generate random parameters for  (use 0 to disable MC simulation) 
	 * @param nbRealizations The number of realizations to generate random parameters for (use 0 to disable MC simulation)
	 * 			 
	 * @return the predictions two different maps : one for PREDICTIONS and one for PREDICTION_VARIANCE
	 * @throws MetaModelException if the meta-model has not converged
	 */	
	public LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, Double>>> getMonteCarloPredictions(int[] ageYr, int timeSinceInitialDateYr, int nbSubjects, int nbRealizations) throws MetaModelException {
		if (hasConverged()) {
			boolean randomEffectVariabilityEnabled = nbSubjects > 0;
			boolean parameterVariabilityEnabled = nbRealizations > 0;
			
			List<Matrix> parmDeviates = new ArrayList<Matrix>();
			for (int i = 0; i < nbRealizations; i++) {
				parmDeviates.add(getParameterEstimateGenerator().getRandomDeviate());
			}
			
			double varianceRandomEffect = model instanceof AbstractMixedModelFullImplementation ? 
					model.getParameters().getValueAt(((AbstractMixedModelFullImplementation)model).indexRandomEffectVariance, 0) : 0.0;
					
			double stdRandomEffect = Math.sqrt(varianceRandomEffect);
				
			int ns = randomEffectVariabilityEnabled ? nbSubjects : 1;
			int nr = parameterVariabilityEnabled ? nbRealizations : 1;
			LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, Double>>> result = new LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, Double>>>();
			for (int i = 0; i < nr; i++) {
				result.put(i, new LinkedHashMap<Integer, LinkedHashMap<Integer, Double>>());
				for (int j = 0; j < ns; j++) {
					result.get(i).put(j, new LinkedHashMap<Integer, Double>());
					double rj = randomEffectVariabilityEnabled ? StatisticalUtility.getRandom().nextGaussian() * stdRandomEffect: 0.0;
					for (int k = 0; k < ageYr.length; k++) {						
						double pred = model.getPrediction(ageYr[k], 
								timeSinceInitialDateYr, 
								rj, 
								parameterVariabilityEnabled ? parmDeviates.get(i) : getFinalParameterEstimates());
								
						result.get(i).get(j).put(ageYr[k], pred);
					}
				}
			}
			
			lastAccessed = LocalDateTime.now();
						
			return result;
		} else {
			throw new MetaModelException("The meta-model has not converged or has not been fitted yet!");
		}
	}
	
	public double getPredictionVariance(int ageYr, int timeSinceInitialDateYr, boolean includeRandomEffectVariance) throws MetaModelException {
		if (hasConverged()) {
			double variance = model.getPredictionVariance(ageYr, timeSinceInitialDateYr, 0d);
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

//	/**
//	 * Export the initial data set (before fitting the meta-model).
//	 * @param filename
//	 * @throws Exception
//	 */
//	public void exportInitialDataSet(String filename) throws Exception {
//		getDataStructureReady().getDataSet().save(filename);
//	}

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
		if (model != null) {
			return model.getSelectedOutputType();
		} else {
			return "";
		}
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

	@Override
	public void save(String filename) throws IOException {
		XmlSerializer serializer = new XmlSerializer(filename);
		serializer.writeObject(this);
		
		MetaDataHelper helper = new MetaDataHelper(); 
		MetaModelMetaData data = helper.generate();
				
		FileOutputStream os = new FileOutputStream(FileUtility.replaceExtensionBy(filename, "json"));
		
		Map<String, Object> options = new HashMap<String, Object>();
		options.put(JsonWriter.PRETTY_PRINT, true);
		options.put(JsonWriter.DATE_FORMAT, JsonWriter.ISO_DATE_TIME_FORMAT+"Z");
		JsonWriter jw = new JsonWriter(os, options);
		jw.write(data);	
		jw.close();
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
	boolean isVarianceAvailable() {
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
}
