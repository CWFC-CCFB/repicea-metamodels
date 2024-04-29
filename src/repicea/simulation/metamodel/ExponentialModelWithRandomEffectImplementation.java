/*
 * This file is part of the repicea-metamodels library.
 *
 * Copyright (C) 2024 His Majesty the King in Right of Canada
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import repicea.math.Matrix;
import repicea.simulation.metamodel.ParametersMapUtilities.FormattedParametersMapKey;
import repicea.simulation.metamodel.ParametersMapUtilities.InputParametersMapKey;
import repicea.stats.data.StatisticalDataException;
import repicea.stats.distributions.ContinuousDistribution;
import repicea.stats.distributions.GaussianDistribution;
import repicea.stats.estimators.mcmc.MetropolisHastingsPriorHandler;

/**
 * An implementation of the exponential model with random effects.
 * @author Mathieu Fortin - April 2024
 */
class ExponentialModelWithRandomEffectImplementation extends AbstractMixedModelFullImplementation {

	ExponentialModelWithRandomEffectImplementation(String outputType, MetaModel model, LinkedHashMap<String, Object>[] startingValues) throws StatisticalDataException {
		super(outputType, model, startingValues);
	}
	
	@Override
	double getPrediction(double ageYr, double timeSinceBeginning, double r1, Matrix parameters) {
		Matrix params = parameters == null ? getParameters() : parameters; 
		
		double b1 = params.getValueAt(0, 0);
		double b2 = params.getValueAt(1, 0);
		double pred = (b1 + r1) * Math.exp(-b2 * ageYr);
		return pred;
	}

	@Override
	public GaussianDistribution getStartingParmEst(double coefVar) {
		fixedEffectsParameterIndices = new ArrayList<Integer>();
		fixedEffectsParameterIndices.add(0);
		fixedEffectsParameterIndices.add(1);
		
		indexCorrelationParameter = 2;
		indexRandomEffectStandardDeviation = 3;
		indexResidualErrorVariance = 4;
		indexFirstRandomEffect = !isVarianceErrorTermAvailable ? 
				indexResidualErrorVariance + 1 : 
					indexResidualErrorVariance;
		
		Matrix parmEst = new Matrix(indexFirstRandomEffect + dataBlockWrappers.size(),1);
		setFixedEffectStartingValuesFromParametersMap(parmEst);
		for (int i = 0; i < dataBlockWrappers.size(); i++) {
			parmEst.setValueAt(indexFirstRandomEffect + i, 0, 0);
		}
		
		Matrix varianceDiag = new Matrix(parmEst.m_iRows,1);
		for (int i = 0; i < varianceDiag.m_iRows; i++) {
			double varianceSampler = i < indexFirstRandomEffect ?
					Math.pow(parmEst.getValueAt(i, 0) * coefVar, 2d) :
						Math.pow(parmEst.getValueAt(indexRandomEffectStandardDeviation, 0) * coefVar, 2d);
			varianceDiag.setValueAt(i, 0, varianceSampler);
		}
		
		GaussianDistribution gd = new GaussianDistribution(parmEst, varianceDiag.matrixDiagonal());
		
		return gd;
	}




	@Override
	Matrix getFirstDerivative(double ageYr, double timeSinceBeginning, double r1) {
		double b1 = getParameters().getValueAt(0, 0);
		double b2 = getParameters().getValueAt(1, 0);
		
		double exp = Math.exp(-b2 * ageYr);
		
		Matrix derivatives = new Matrix(2,1);
		derivatives.setValueAt(0, 0, exp);
		derivatives.setValueAt(1, 0, - ageYr * (b1 + r1) * exp);
		return derivatives;
	}

	@Override
	public boolean isInterceptModel() {return false;}

	@Override
	public List<String> getEffectList() {
		return Arrays.asList(new String[] {"b1","b2"});
	}

	@Override
	List<String> getParameterNames() {
		return Arrays.asList(isVarianceErrorTermAvailable ?
				new String[] {"b1", "b2", AbstractModelImplementation.CORRELATION_PARM, AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD} :
					new String[] {"b1", "b2", AbstractModelImplementation.CORRELATION_PARM, AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD, AbstractModelImplementation.RESIDUAL_VARIANCE});
	}


	@Override
	public String getModelDefinition() {
		return "y ~ (b1 + u_i)*exp(-b2*t)";
	}

	@Override
	public void setPriorDistributions(MetropolisHastingsPriorHandler handler) {
		super.setPriorDistributions(handler);
		ContinuousDistribution stdPrior = (ContinuousDistribution) parametersMap.get(AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD).get(FormattedParametersMapKey.PriorDistribution);
		for (int i = 0; i < dataBlockWrappers.size(); i++) {
			handler.addRandomEffectStandardDeviation(new GaussianDistribution(0, 1), stdPrior, indexFirstRandomEffect + i);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	LinkedHashMap<String, Object>[] getDefaultParameters() {
		 LinkedHashMap<String, Object>[] inputMap = new LinkedHashMap[5];
		 LinkedHashMap<String, Object> oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b1");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 2000 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "8000"});
		 inputMap[0] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b2");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.005 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.00001", "0.05"});
		 inputMap[1] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractModelImplementation.CORRELATION_PARM);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.92 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.80", "0.995"});
		 inputMap[2] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 1000 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "3000"});
		 inputMap[3] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractModelImplementation.RESIDUAL_VARIANCE);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 10000 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "20000"});
		 inputMap[4] = oMap;
		 return inputMap;
	}

}
