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
import repicea.simulation.metamodel.ParametersMapUtilities.InputParametersMapKey;
import repicea.stats.data.StatisticalDataException;
import repicea.stats.distributions.GaussianDistribution;

/**
 * A modified implementation of the Chapman-Richards derivative model.<p>
 * This model is meant to work with stem density.
 * @author Mathieu Fortin - April 2024
 */
class FourParameterChapmanRichardsDerivativeModelImplementation extends AbstractModelImplementation {

	protected FourParameterChapmanRichardsDerivativeModelImplementation(String outputType, MetaModel model, LinkedHashMap<String, Object>[] startingValues) throws StatisticalDataException {
		super(outputType, model, startingValues);
	}

	@Override
	double getPrediction(double ageYr, double timeSinceBeginning, double r1, Matrix parameters) {
		Matrix params = parameters == null ? getParameters() : parameters;
		double b1 = params.getValueAt(0, 0);
		double b2 = params.getValueAt(1, 0);
		double b3 = params.getValueAt(2, 0);
		double b4 = params.getValueAt(3, 0);
		double pred = (b1 + r1) * Math.exp(-b4 * ageYr) * Math.pow(1 - Math.exp(-b2 * ageYr), b3);
		return pred;
	}

	@Override
	public GaussianDistribution getStartingParmEst(double coefVar) {
		fixedEffectsParameterIndices = new ArrayList<Integer>();
		fixedEffectsParameterIndices.add(0);
		fixedEffectsParameterIndices.add(1);
		fixedEffectsParameterIndices.add(2);
		fixedEffectsParameterIndices.add(3);

		indexCorrelationParameter = 4;
		indexResidualErrorVariance = 5;

		int lastIndex = !isVarianceErrorTermAvailable ? 
				indexResidualErrorVariance + 1: 
					indexResidualErrorVariance;

		Matrix parmEst = new Matrix(lastIndex,1);
		setFixedEffectStartingValuesFromParametersMap(parmEst);

		Matrix varianceDiag = new Matrix(parmEst.m_iRows,1);
		for (int i = 0; i < varianceDiag.m_iRows; i++) {
			varianceDiag.setValueAt(i, 0, Math.pow(parmEst.getValueAt(i, 0) * coefVar, 2d));
		}
		
		GaussianDistribution gd = new GaussianDistribution(parmEst, varianceDiag.matrixDiagonal());

		return gd;
	}

	@Override
	Matrix getFirstDerivative(double ageYr, double timeSinceBeginning, double r1) {
		return getDerivatives(getParameters(), ageYr, timeSinceBeginning, r1);
	}

	static Matrix getDerivatives(Matrix parms, double ageYr, double timeSinceBeginning, double r1) {
		double b1 = parms.getValueAt(0, 0);
		double b2 = parms.getValueAt(1, 0);
		double b3 = parms.getValueAt(2, 0);
		double b4 = parms.getValueAt(3, 0);
		
		double exp1 = Math.exp(-b4 * ageYr);
		double exp2 = Math.exp(-b2 * ageYr);
		double root = 1 - exp2;
		
		Matrix derivatives = new Matrix(4,1);
		derivatives.setValueAt(0, 0, exp1 * Math.pow(root, b3));
		derivatives.setValueAt(1, 0, (b1 + r1) * exp1 * b3 * Math.pow(root, b3 - 1) * exp2 * ageYr);
		derivatives.setValueAt(2, 0, (b1 + r1) * exp1 * Math.pow(root, b3) * Math.log(root));
		derivatives.setValueAt(3, 0, -ageYr * (b1 + r1) * exp1 * Math.pow(root, b3));
		return derivatives;
	}
	
	
	@Override
	public boolean isInterceptModel() {return false;}

	@Override
	public List<String> getEffectList() {
		return Arrays.asList(new String[] {"b1","b2","b3","b4"});
	}

	@Override
	List<String> getParameterNames() {
		return Arrays.asList(isVarianceErrorTermAvailable ? 				
				new String[] {"b1", "b2", "b3", "b4", AbstractModelImplementation.CORRELATION_PARM} :
					new String[] {"b1", "b2", "b3", "b4", AbstractModelImplementation.CORRELATION_PARM, AbstractModelImplementation.RESIDUAL_VARIANCE});
	}

	@Override
	public String getModelDefinition() {
		return "y ~ b1*exp(-b4*t)*(1-exp(-b2*t))^b3";
	}

	@SuppressWarnings("unchecked")
	@Override
	LinkedHashMap<String, Object>[] getDefaultParameters() {
		 LinkedHashMap<String, Object>[] inputMap = new LinkedHashMap[6];
		 LinkedHashMap<String, Object> oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b1");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 5000 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "10000"});
		 inputMap[0] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b2");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.02 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.00001", "0.2"});
		 inputMap[1] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b3");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 1 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.1", "4"});
		 inputMap[2] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b4");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.006 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.001", "0.01"});
		 inputMap[3] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractModelImplementation.CORRELATION_PARM);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.92 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.80", "0.995"});
		 inputMap[4] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractModelImplementation.RESIDUAL_VARIANCE);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 2500 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "5000"});
		 inputMap[5] = oMap;
		 return inputMap;
	}

}
