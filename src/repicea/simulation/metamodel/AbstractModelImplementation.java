/*
 * This file is part of the repicea-metamodels library.
 *
 * Copyright (C) 2021-24 His Majesty the King in Right of Canada
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

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import repicea.math.Matrix;
import repicea.math.SymmetricMatrix;
import repicea.simulation.metamodel.MetaModel.ModelImplEnum;
import repicea.simulation.metamodel.ParametersMapUtilities.FormattedParametersMapKey;
import repicea.simulation.scriptapi.ScriptResult;
import repicea.stats.StatisticalUtility;
import repicea.stats.StatisticalUtility.TypeMatrixR;
import repicea.stats.data.DataBlock;
import repicea.stats.data.DataSet;
import repicea.stats.data.GenericHierarchicalStatisticalDataStructure;
import repicea.stats.data.HierarchicalStatisticalDataStructure;
import repicea.stats.data.Observation;
import repicea.stats.data.StatisticalDataException;
import repicea.stats.distributions.ContinuousDistribution;
import repicea.stats.distributions.GaussianDistribution;
import repicea.stats.distributions.UniformDistribution;
import repicea.stats.estimators.mcmc.MetropolisHastingsAlgorithm;
import repicea.stats.estimators.mcmc.MetropolisHastingsCompatibleModel;
import repicea.stats.estimators.mcmc.MetropolisHastingsPriorHandler;
import repicea.stats.model.StatisticalModel;

/**
 * A package class to handle the different types of meta-models (e.g. Chapman-Richards and others).
 * @author Mathieu Fortin - September 2021
 */
abstract class AbstractModelImplementation implements StatisticalModel, MetropolisHastingsCompatibleModel, Runnable {

	protected static final String RESIDUAL_VARIANCE = "sigma2_res";
	protected static final String CORRELATION_PARM = "rho";
	protected static final String REG_LAG_PARM = "regLag";
	
	protected static final List<String> NUISANCE_PARMS = new ArrayList<String>();
	static {
		NUISANCE_PARMS.add(REG_LAG_PARM);
	}
	
	/**
	 * A nested class to handle blocks of repeated measurements.
	 * @author Mathieu Fortin - November 2021
	 */
	@SuppressWarnings("serial")
	final class DataBlockWrapper extends AbstractDataBlockWrapper {

		Matrix varCovFullCorr;
		final Matrix distances;
		Matrix invVarCov;
		double lnConstant;
		final int nbPlots;

		DataBlockWrapper(String blockId, 
				List<Integer> indices, 
				Matrix vectorY,
				Matrix matrixX,
				Matrix overallVarCov,
				int nbPlots) {
			super(blockId, indices, vectorY, matrixX, overallVarCov);
			if (AbstractModelImplementation.this.isVarianceErrorTermAvailable) { 
				Matrix varCovTmp = overallVarCov.getSubMatrix(indices, indices);
				Matrix stdDiag = correctVarCov(varCovTmp).diagonalVector().elementWisePower(0.5);
				this.varCovFullCorr = stdDiag.multiply(stdDiag.transpose());
			}
			this.nbPlots = nbPlots;
			distances = new Matrix(this.indices.size(), 1, 1, 1);
		}

		@Override
		void updateCovMat(Matrix parameters) {
			if (!AbstractModelImplementation.this.isVarianceErrorTermAvailable) {	// The residual variance is then a parameter to be estimated
				int resVarIndex = parameterIndexMap.get(RESIDUAL_VARIANCE);
				double resVariance = parameters.getValueAt(resVarIndex, 0);
				this.varCovFullCorr = new Matrix(indices.size(), indices.size(), resVariance / nbPlots, 0); 
			}
			int corrParmIndex = parameterIndexMap.get(CORRELATION_PARM);
			double rhoParm = parameters.getValueAt(corrParmIndex, 0);	
			SymmetricMatrix corrMat = StatisticalUtility.constructRMatrix(Arrays.asList(new Double[] {1d, rhoParm}), TypeMatrixR.POWER, distances);
			Matrix varCov = varCovFullCorr.elementWiseMultiply(corrMat);

			Matrix invCorr = StatisticalUtility.getInverseCorrelationAR1Matrix(distances.m_iRows, rhoParm);
			Matrix invFull = varCovFullCorr.elementWisePower(-1d);
			invVarCov = invFull.elementWiseMultiply(invCorr);
			double determinant = varCov.getDeterminant();
			int k = this.vecY.m_iRows;
			this.lnConstant = -.5 * k * Math.log(2 * Math.PI) - Math.log(determinant) * .5;
		}

		@Override
		double getLogLikelihood() {
			Matrix pred = generatePredictions(this, getParameterValue(0));
			Matrix residuals = vecY.subtract(pred);
			Matrix rVr = residuals.transpose().multiply(invVarCov).multiply(residuals);
			double rVrValue = rVr.getSumOfElements();
			if (rVrValue < 0) {
				throw new UnsupportedOperationException("The sum of squared errors is negative!");
			} else {
				double llk = - 0.5 * rVrValue + lnConstant; 
				return llk;
			}
		}

	}
	
	private static final Map<Class<? extends AbstractModelImplementation>, ModelImplEnum> EnumMap = new HashMap<Class<? extends AbstractModelImplementation>, ModelImplEnum>();
	static {
		EnumMap.put(ChapmanRichardsModelImplementation.class, ModelImplEnum.ChapmanRichards);
		EnumMap.put(ChapmanRichardsModelWithRandomEffectImplementation.class, ModelImplEnum.ChapmanRichardsWithRandomEffect);
		EnumMap.put(ChapmanRichardsDerivativeModelImplementation.class, ModelImplEnum.ChapmanRichardsDerivative);
		EnumMap.put(ChapmanRichardsDerivativeModelWithRandomEffectImplementation.class, ModelImplEnum.ChapmanRichardsDerivativeWithRandomEffect);
		EnumMap.put(ExponentialModelImplementation.class, ModelImplEnum.Exponential);
		EnumMap.put(ExponentialModelWithRandomEffectImplementation.class, ModelImplEnum.ExponentialWithRandomEffect);
		EnumMap.put(ModifiedChapmanRichardsDerivativeModelImplementation.class, ModelImplEnum.ModifiedChapmanRichardsDerivative);
		EnumMap.put(ModifiedChapmanRichardsDerivativeModelWithRandomEffectImplementation.class, ModelImplEnum.ModifiedChapmanRichardsDerivativeWithRandomEffect);
	}
	
	static boolean EstimateResidualVariance = false;  
	
	protected final List<AbstractDataBlockWrapper> dataBlockWrappers;
	protected final String outputType;
	protected final String stratumGroup;
	protected final MetropolisHastingsAlgorithm mh;
	private Matrix parameters;
	private Matrix parmsVarCov;
	protected List<Integer> fixedEffectsParameterIndices;
	protected LinkedHashMap<String, Integer> parameterIndexMap;
	protected List<String> parameterNames;
	private DataSet finalDataSet;
	protected final boolean isVarianceErrorTermAvailable;
	protected final boolean isRegenerationLagEvaluationNeeded;
	protected final Map<String, Map<FormattedParametersMapKey, Object>> parametersMap; 

	protected final int ageYrLimitBelowWhichThereIsLikelyRegenerationLag = 10;

	/**
	 * Internal constructor.
	 * @param outputType the desired outputType to be modelled
	 * @param scriptResults a Map containing the ScriptResult instances of the growth simulation
	 */
	AbstractModelImplementation(String outputType, MetaModel metaModel, Map<String, Object>[] startingValues) throws StatisticalDataException {
		Map<Integer, ScriptResult> scriptResults = metaModel.scriptResults;
		String stratumGroup = metaModel.getStratumGroup();
		if (stratumGroup == null) {
			throw new InvalidParameterException("The argument stratumGroup must be non null!");
		}
		if (outputType == null) {
			throw new InvalidParameterException("The argument outputType must be non null!");
		}
		if (!MetaModel.getPossibleOutputTypes(scriptResults).contains(outputType)) {
			throw new InvalidParameterException("The outputType " + outputType + " is not part of the dataset!");
		}
		this.stratumGroup = stratumGroup;
		HierarchicalStatisticalDataStructure structure = getDataStructureReady(outputType, scriptResults);
		isVarianceErrorTermAvailable = metaModel.isVarianceAvailable() && !AbstractModelImplementation.EstimateResidualVariance;
		Matrix varCov = getVarCovReady(outputType, scriptResults);

		this.outputType = outputType;
		Map<String, DataBlock> formattedMap = new LinkedHashMap<String, DataBlock>();
		Map<String, DataBlock> ageMap = structure.getHierarchicalStructure(); 
		for (String ageKey : ageMap.keySet()) {
			DataBlock db = ageMap.get(ageKey);
			for (String speciesGroupKey : db.keySet()) {
				DataBlock innerDb = db.get(speciesGroupKey);
				formattedMap.put(ageKey + "_" + speciesGroupKey, innerDb);
			}
		}

		dataBlockWrappers = new ArrayList<AbstractDataBlockWrapper>();
		Matrix vectorY = structure.constructVectorY();
		Matrix matrixX = structure.constructMatrixX();
		int minimumStratumAgeYr = Integer.MAX_VALUE;
		for (String k : formattedMap.keySet()) {
			DataBlock db = formattedMap.get(k);
			List<Integer> indices = db.getIndices();
			int age = Integer.parseInt(k.substring(0, k.indexOf("_")));
			int nbPlots = scriptResults.get(age).getNbPlots();
			AbstractDataBlockWrapper bw = createWrapper(k, indices, vectorY, matrixX, varCov, nbPlots);
			dataBlockWrappers.add(bw);
			if (bw.getInitialAgeYr() < minimumStratumAgeYr) {
				minimumStratumAgeYr = bw.getInitialAgeYr();
			}
		}
		
		isRegenerationLagEvaluationNeeded = minimumStratumAgeYr <= ageYrLimitBelowWhichThereIsLikelyRegenerationLag;
		
//		finalDataSet = structure.getDataSet();
		mh = new MetropolisHastingsAlgorithm(this, MetaModelManager.LoggerName, getLogMessagePrefix());
		mh.setSimulationParameters(metaModel.mhSimParms);
		
		Map<String, Object>[] unformattedMap = startingValues == null ?
				getDefaultParameters() :
				startingValues;
		parametersMap = ParametersMapUtilities.formatParametersMap(unformattedMap, getParameterNames(), NUISANCE_PARMS);
	}

	abstract LinkedHashMap<String, Object>[] getDefaultParameters();
	abstract List<String> getParameterNames();

	protected final AbstractDataBlockWrapper createWrapper(String k, 
			List<Integer> indices, 
			Matrix vectorY, 
			Matrix matrixX, 
			Matrix varCov, 
			int nbPlots) {
		return new DataBlockWrapper(k, indices, vectorY, matrixX, varCov, nbPlots);
	}
	
	private Matrix generatePredictions(AbstractDataBlockWrapper dbw, double randomEffect, boolean includePredVariance) {
		boolean canCalculateVariance = includePredVariance && mh.getParameterCovarianceMatrix() != null;
		Matrix mu = canCalculateVariance ? new Matrix(dbw.vecY.m_iRows, 2) : new Matrix(dbw.vecY.m_iRows, 1);
		
		Matrix correctedAgeYr = dbw.getInitialAgeYr() <= ageYrLimitBelowWhichThereIsLikelyRegenerationLag ?
				dbw.ageYr.scalarAdd(-getParameters().getValueAt(parameterIndexMap.get(REG_LAG_PARM), 0)) : // we subtract the regeneration lag for young stratum
					dbw.ageYr;
		
//		if (correctedAgeYr.subtract(dbw.ageYr).getAbsoluteValue().anyElementLargerThan(1E-4)) {
//			int u = 0;
//		}
		
		for (int i = 0; i < mu.m_iRows; i++) {
			mu.setValueAt(i, 0, getPrediction(correctedAgeYr.getValueAt(i, 0), dbw.timeSinceBeginning.getValueAt(i, 0), randomEffect));
			if (canCalculateVariance) {
				double predVar = getPredictionVariance(correctedAgeYr.getValueAt(i, 0), dbw.timeSinceBeginning.getValueAt(i, 0), randomEffect);
				mu.setValueAt(i, 1, predVar);
			}
		}
		return mu;
	}

	@Override
	public void setPriorDistributions(MetropolisHastingsPriorHandler handler) {
		handler.clear();
		setPriorsFromParametersMap(handler);
	}
	
	protected final ModelImplEnum getModelImplementation() {
		return EnumMap.get(getClass());
	}
	
	protected final void setFixedEffectStartingValuesFromParametersMap(Matrix parmEst) {
		for (String paramName : getParameterNames()) {
			int index = this.parameterIndexMap.get(paramName);
			if (paramName.equals(REG_LAG_PARM)) {
				parmEst.setValueAt(index, 0, 0d);  // the lag is 0 by default
			} else {	
				parmEst.setValueAt(index, 0, (Double) parametersMap.get(paramName).get(FormattedParametersMapKey.StartingValue));
			} 
		}
	}
	
	protected final void setPriorsFromParametersMap(MetropolisHastingsPriorHandler handler) {
		for (String paramName : getParameterNames()) {
			int index = getParameterNames().indexOf(paramName);
			if (paramName.equals(REG_LAG_PARM)) {
				handler.addFixedEffectDistribution(new UniformDistribution(new Matrix(1,1), 
						new Matrix(1,1,ageYrLimitBelowWhichThereIsLikelyRegenerationLag,0)), index);
			} else  {
				handler.addFixedEffectDistribution((ContinuousDistribution) parametersMap.get(paramName).get(FormattedParametersMapKey.PriorDistribution), index);
			}
		}
	}

	@Override
	public final double getLogLikelihood(Matrix parameters) {
		setParameters(parameters);
		double logLikelihood = 0d;
		for (int i = 0; i < dataBlockWrappers.size(); i++) {
			double logLikelihoodForThisBlock = getLogLikelihoodForThisBlock(parameters, i);
			logLikelihood += logLikelihoodForThisBlock;
		}
		return logLikelihood;
	}
	
	
	protected double getLogLikelihoodForThisBlock(Matrix parameters, int i) {
		AbstractDataBlockWrapper dbw = dataBlockWrappers.get(i);
		return dbw.getLogLikelihood();
	}
	
	/**
	 * Get the observations of a particular output type ready for the meta-model fitting. 
	 * @return a HierarchicalStatisticalDataStructure instance
	 * @param outputType the desired outputType to be modelled
	 * @param scriptResults a Map containing the ScriptResult instances of the growth simulation
	 * @throws StatisticalDataException
	 */
	private HierarchicalStatisticalDataStructure getDataStructureReady(String outputType, Map<Integer, ScriptResult> scriptResults) throws StatisticalDataException {
		finalDataSet = null;
		for (int initAgeYr : scriptResults.keySet()) {
			ScriptResult r = scriptResults.get(initAgeYr);
			DataSet dataSet = r.getDataSet();
			if (finalDataSet == null) {
				List<String> fieldNames = new ArrayList<String>();
				fieldNames.addAll(dataSet.getFieldNames());
				fieldNames.add("initialAgeYr");
				finalDataSet = new DataSet(fieldNames);
			}
			int outputTypeFieldNameIndex = finalDataSet.getFieldNames().indexOf(ScriptResult.OutputTypeFieldName);
			for (Observation obs : dataSet.getObservations()) {
				List<Object> newObs = new ArrayList<Object>();
				Object[] obsArray = obs.toArray();
				if (obsArray[outputTypeFieldNameIndex].equals(outputType)) {
					newObs.addAll(Arrays.asList(obsArray));
					newObs.add(initAgeYr);	// adding the initial age to the data set
					finalDataSet.addObservation(newObs.toArray());
				}
			}
		}
		finalDataSet.indexFieldType();
		HierarchicalStatisticalDataStructure dataStruct = new GenericHierarchicalStatisticalDataStructure(finalDataSet, false);	// no sorting
		dataStruct.setInterceptModel(false); // no intercept
		dataStruct.setModelDefinition("Estimate ~ initialAgeYr + timeSinceInitialDateYr + (1 | initialAgeYr/OutputType)");
		return dataStruct;
	}
	
	/**
	 * Format the variance-covariance matrix of the residual error term. <br>
	 * <br>
	 * If the model does not provide the variance associated with the predictions,
	 * this method returns null.
	 * @param outputType the desired outputType to be modelled
	 * @param scriptResults a Map containing the ScriptResult instances of the growth simulation
	 * @return
	 */
	private Matrix getVarCovReady(String outputType, Map<Integer, ScriptResult> scriptResults) {
		if (this.isVarianceErrorTermAvailable) {
			Matrix varCov = null;
			for (int initAgeYr : scriptResults.keySet()) {
				ScriptResult r = scriptResults.get(initAgeYr);
				Matrix varCovI = r.computeVarCovErrorTerm(outputType);
				if (varCov == null) {
					varCov = varCovI;
				} else {
					varCov = varCov.matrixDiagBlock(varCovI);
				}
			}
			return varCov;
		} else {
			return null;
		}
	}

	/**
	 * Entry point for log-likelihood calculation.
	 * @param dbw an AbstractDataBlockWrapper dbw
	 * @param randomEffect a random effect
	 * @return a Matrix of predictions
	 */
	private Matrix generatePredictions(AbstractDataBlockWrapper dbw, double randomEffect) {
		return generatePredictions(dbw, randomEffect, false);
	}
	
	final double getPrediction(double ageYr, double timeSinceBeginning, double r1) {
		return getPrediction(ageYr, timeSinceBeginning, r1, null);
	}
	
	abstract double getPrediction(double ageYr, double timeSinceBeginning, double r1, Matrix parameters);
	
	abstract Matrix getFirstDerivative(double ageYr, double timeSinceBeginning, double r1);

	/*
	 * This method has to be synchronized to avoid ConcurrentModificationException.<p>
	 * The ConcurrentModificationException arises in the Matrix.getSubMatrix method when
	 * it sorts the vector of integers. 
	 */
	final synchronized double getPredictionVariance(double ageYr, double timeSinceBeginning, double r1) {
		if (mh.getParameterCovarianceMatrix() == null) {
			throw new InvalidParameterException("The variance-covariance matrix of the parameter estimates has not been set!");
		}
		Matrix firstDerivatives = getFirstDerivative(ageYr, timeSinceBeginning, r1);
		Matrix variance = firstDerivatives.transpose().multiply(mh.getParameterCovarianceMatrix().getSubMatrix(fixedEffectsParameterIndices, fixedEffectsParameterIndices)).multiply(firstDerivatives);
		return variance.getValueAt(0, 0);
	}
	

	protected void setParameters(Matrix parameters) {
		this.parameters = parameters;
		for (AbstractDataBlockWrapper dbw : dataBlockWrappers) {
			dbw.updateCovMat(parameters);
		}

	}
	
	@Override
	public Matrix getParameters() {
		return parameters;
	}
	
	Matrix getParmsVarCov() {
		return parmsVarCov;
	}
	
	private void setParmsVarCov(Matrix m) {
		parmsVarCov = m;
	}

	String getSelectedOutputType() {
		return outputType;
	}
	

	private String getLogMessagePrefix() {
		return stratumGroup + " Implementation " + getModelImplementation().name();
	}

	@Override
	public void doEstimation() {
		mh.doEstimation();
		if (mh.isConvergenceAchieved()) {
			setParameters(mh.getFinalParameterEstimates());
			setParmsVarCov(mh.getParameterCovarianceMatrix());
		}
	}
	

	boolean hasConverged() {return mh.isConvergenceAchieved();}
	
	@Override
	public final void run() {
		doEstimation();
	}
	
	DataSet getFinalDataSet() {
		return finalDataSet;
	}

	@Override
	public MetropolisHastingsAlgorithm getEstimator() {
		return mh;
	}
	
	@Override
	public final int getNbSubjects() {
		return dataBlockWrappers.size();
	}
	
	@Override
	public final double getLikelihoodOfThisSubject(Matrix m, int i) {
		setParameters(m);
		return Math.exp(getLogLikelihoodForThisBlock(m, i));
	}
	
	@Override
	public int getNumberOfObservations() {
		return finalDataSet.getNumberOfObservations();
	}

	public abstract String getModelDefinition();
	
	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
	
	@Override
	public List<String> getOtherParameterNames() {
		List<String> parameters = new ArrayList<String>();
		parameters.add(CORRELATION_PARM);
		if (!isVarianceErrorTermAvailable)
			parameters.add(RESIDUAL_VARIANCE);
		if (isRegenerationLagEvaluationNeeded) 
			parameters.add(REG_LAG_PARM);
		return parameters;
	}

	/**
	 * Provide the sampler variance.
	 * @param parameters a Matrix instance with all the parameters (including random effects if any)
	 * @param coefVar a factor to modulate the variance
	 * @return a Matrix instance
	 */
	Matrix calculateSamplerVariance(Matrix parameters, double coefVar) {
		int resLagIndex = parameterIndexMap.containsKey(REG_LAG_PARM) ?
				parameterIndexMap.get(REG_LAG_PARM) :
					-1;
		Matrix varianceDiag = new Matrix(parameters.m_iRows,1);
		for (int i = 0; i < varianceDiag.m_iRows; i++) {
			double parmValue = i == resLagIndex ?
					ageYrLimitBelowWhichThereIsLikelyRegenerationLag :
					parameters.getValueAt(i, 0);
			varianceDiag.setValueAt(i, 0, Math.pow(parmValue * coefVar, 2d));
		}
		return varianceDiag;
	}

	@Override
	public GaussianDistribution getStartingParmEst(double coefVar) {
		int nbParameters = parameterIndexMap.size();
		Matrix parmEst = new Matrix(nbParameters, 1);
		setFixedEffectStartingValuesFromParametersMap(parmEst);
		Matrix varianceDiag = calculateSamplerVariance(parmEst, coefVar);
		GaussianDistribution gd = new GaussianDistribution(parmEst, varianceDiag.matrixDiagonal());
		return gd;
	}

	double getRegenerationLagYrIfAny() {
		if (parameterIndexMap.containsKey(REG_LAG_PARM)) {
			int index = parameterIndexMap.get(REG_LAG_PARM);
			return getParameters().getValueAt(index, 0);
		} else {
			return 0d;
		}
	}
}
