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
 * An implementation of the derivative form of the Chapman-Richards model including random effects.
 * @author Mathieu Fortin - October 2021
 */
class ChapmanRichardsDerivativeModelWithRandomEffectImplementation extends AbstractMixedModelFullImplementation {
	
	ChapmanRichardsDerivativeModelWithRandomEffectImplementation(String outputType, MetaModel model, LinkedHashMap<String, Object>[] startingValues) throws StatisticalDataException {
		super(outputType, model, startingValues);
	}
	
	@Override
	public GaussianDistribution getStartingParmEst(double coefVar) {
		indexCorrelationParameter = 3;
		indexRandomEffectStandardDeviation = 4;
		indexResidualErrorVariance = 5;
		indexFirstRandomEffect = !isVarianceErrorTermAvailable ? 
				indexResidualErrorVariance + 1 : 
					indexResidualErrorVariance;
		
		Matrix parmEst = new Matrix(indexFirstRandomEffect + dataBlockWrappers.size(),1);
		setFixedEffectStartingValuesFromParametersMap(parmEst);
		for (int i = 0; i < dataBlockWrappers.size(); i++) {
			parmEst.setValueAt(indexFirstRandomEffect + i, 0, 0);
		}
		
		fixedEffectsParameterIndices = new ArrayList<Integer>();
		fixedEffectsParameterIndices.add(0);
		fixedEffectsParameterIndices.add(1);
		fixedEffectsParameterIndices.add(2);

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
	double getPrediction(double ageYr, double timeSinceBeginning, double r1, Matrix parameters) {
		Matrix params = parameters == null ? getParameters() : parameters; 
		
		double b1 = params.getValueAt(0, 0);
		double b2 = params.getValueAt(1, 0);
		double b3 = params.getValueAt(2, 0);
		double pred = (b1 + r1) * Math.exp(-b2 * ageYr) * Math.pow(1 - Math.exp(-b2 * ageYr), b3);
		return pred;
	}


	@Override
	Matrix getFirstDerivative(double ageYr, double timeSinceBeginning, double r1) {
		double b1 = getParameters().getValueAt(0, 0);
		double b2 = getParameters().getValueAt(1, 0);
		double b3 = getParameters().getValueAt(2, 0);
		
		double exp = Math.exp(-b2 * ageYr);
		double root = 1 - exp;
		
		Matrix derivatives = new Matrix(3,1);
		derivatives.setValueAt(0, 0, exp * Math.pow(root, b3));
		derivatives.setValueAt(1, 0, - ageYr * b1 * exp * Math.pow(root, b3) + 
				b1 * exp * b3 * Math.pow(root, b3 - 1) * exp * ageYr);
		derivatives.setValueAt(2, 0, b1 * exp * Math.pow(root, b3) * Math.log(root));
		return derivatives;
	}

	@Override
	public boolean isInterceptModel() {return false;}

	@Override
	public List<String> getEffectList() {
		return Arrays.asList(new String[] {"b1","b2","b3"});
	}

	@Override
	List<String> getParameterNames() {
		return Arrays.asList(isVarianceErrorTermAvailable ?
				new String[] {"b1", "b2", "b3", AbstractModelImplementation.CORRELATION_PARM, AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD} :
					new String[] {"b1", "b2", "b3", AbstractModelImplementation.CORRELATION_PARM, AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD, AbstractModelImplementation.RESIDUAL_VARIANCE});
	}


	@Override
	public String getModelDefinition() {
		return "y ~ (b1 + u_i)*exp(-b2*t)*(1-exp(-b2*t))^b3";
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
		 LinkedHashMap<String, Object>[] inputMap = new LinkedHashMap[6];
		 LinkedHashMap<String, Object> oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b1");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 1000 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "2000"});
		 inputMap[0] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b2");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.02 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.00001", "0.05"});
		 inputMap[1] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b3");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 2 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.8", "6"});
		 inputMap[2] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractModelImplementation.CORRELATION_PARM);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.92 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.80", "0.995"});
		 inputMap[3] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 30 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "125"});
		 inputMap[4] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractModelImplementation.RESIDUAL_VARIANCE);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 250 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "5000"});
		 inputMap[5] = oMap;
		 return inputMap;
	}

}
