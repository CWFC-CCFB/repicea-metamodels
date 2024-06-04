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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import repicea.math.Matrix;
import repicea.simulation.metamodel.ParametersMapUtilities.FormattedParametersMapKey;
import repicea.stats.data.StatisticalDataException;
import repicea.stats.distributions.ContinuousDistribution;
import repicea.stats.distributions.GaussianDistribution;
import repicea.stats.estimators.mcmc.MetropolisHastingsPriorHandler;

/**
 * The AbstractMixedModelImplementation class relies on a marginal log-likelihood instead of a pure 
 * log-likelihood.
 * @author Mathieu Fortin - October 2021
 */
abstract class AbstractMixedModelFullImplementation extends AbstractModelImplementation {
	
	protected static final String RANDOM_EFFECT_STD = "sigma_u";
	
//	int indexRandomEffectStandardDeviation;
//	int indexFirstRandomEffect;
	
	GaussianDistribution randomEffectDistribution;
	
	AbstractMixedModelFullImplementation(String outputType, MetaModel metaModel, LinkedHashMap<String, Object>[] startingValues) throws StatisticalDataException {
		super(outputType, metaModel, startingValues);
	}

	@Override
	protected double getLogLikelihoodForThisBlock(Matrix parameters, int i) {
		AbstractDataBlockWrapper dbw = dataBlockWrappers.get(i);
		double randomEffect = parameters.getValueAt(parameterIndexMap.size() + i, 0);
		dbw.setParameterValue(0, randomEffect);
		return dbw.getLogLikelihood();
	}	
	
	protected double getVarianceDueToRandomEffect(double ageYr, double timeSinceBeginning) {	
		int randomEffectSTDIndex = parameterIndexMap.get(RANDOM_EFFECT_STD);
		double randomEffectStandardDeviation = getParameters().getValueAt(randomEffectSTDIndex, 0);
		double value = getFirstDerivative(ageYr, timeSinceBeginning, 0.0).getValueAt(0, 0);
		return value * value * randomEffectStandardDeviation * randomEffectStandardDeviation;   
	}

	@Override
	public final List<String> getOtherParameterNames() {
		List<String> parameters = new ArrayList<String>();
		parameters.add(AbstractModelImplementation.CORRELATION_PARM);
		parameters.add(AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD);
		if (!isVarianceErrorTermAvailable)
			parameters.add(AbstractModelImplementation.RESIDUAL_VARIANCE);
		if (isRegenerationLagEvaluationNeeded) 
			parameters.add(REG_LAG_PARM);
		for (AbstractDataBlockWrapper w : dataBlockWrappers) {
			parameters.add("u_" + w.blockId.substring(0, w.blockId.indexOf("_")));
		}
		return parameters;
	}

	@Override
	Matrix calculateSamplerVariance(Matrix parameters, double coefVar) {
		int resLagIndex = parameterIndexMap.containsKey(REG_LAG_PARM) ?
				parameterIndexMap.get(REG_LAG_PARM) :
					-1;
		Matrix varianceDiag = new Matrix(parameters.m_iRows,1);
		int randomEffectSTDIndex = parameterIndexMap.get(AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD);
		int nbParameters = parameterIndexMap.size();
		for (int i = 0; i < varianceDiag.m_iRows; i++) {
			double varianceSampler;
			if (i < nbParameters) {
				double parmValue = i == resLagIndex ?
						ageYrLimitBelowWhichThereIsLikelyRegenerationLag :
						parameters.getValueAt(i, 0);
				varianceSampler = Math.pow(parmValue * coefVar, 2d);
			} else {
				varianceSampler = Math.pow(parameters.getValueAt(randomEffectSTDIndex, 0) * coefVar, 2d);
			}
			varianceDiag.setValueAt(i, 0, varianceSampler);
		}
		return varianceDiag;
	}
		
	@Override
	public final GaussianDistribution getStartingParmEst(double coefVar) {
		int nbParameters = parameterIndexMap.size();
		Matrix parmEst = new Matrix(nbParameters + dataBlockWrappers.size(), 1);
		setFixedEffectStartingValuesFromParametersMap(parmEst);
		for (int i = 0; i < dataBlockWrappers.size(); i++) {
			parmEst.setValueAt(nbParameters + i, 0, 0); 
		}
		Matrix varianceDiag = calculateSamplerVariance(parmEst, coefVar);
		GaussianDistribution gd = new GaussianDistribution(parmEst, varianceDiag.matrixDiagonal());
		return gd;
	}
	
	@Override
	public final void setPriorDistributions(MetropolisHastingsPriorHandler handler) {
		super.setPriorDistributions(handler);
		ContinuousDistribution stdPrior = (ContinuousDistribution) parametersMap.get(AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD).get(FormattedParametersMapKey.PriorDistribution);
		int nbParameters = parameterIndexMap.size();
		for (int i = 0; i < dataBlockWrappers.size(); i++) {
			handler.addRandomEffectStandardDeviation(new GaussianDistribution(0, 1), stdPrior, nbParameters + i);
		}
	}


}
