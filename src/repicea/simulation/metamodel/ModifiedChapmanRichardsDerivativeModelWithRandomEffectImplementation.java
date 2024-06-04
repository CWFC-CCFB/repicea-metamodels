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
import java.util.Set;

import repicea.math.Matrix;
import repicea.simulation.metamodel.ParametersMapUtilities.InputParametersMapKey;
import repicea.stats.data.StatisticalDataException;

/**
 * A modified implementation of the Chapman-Richards derivative model with random effects.<p>
 * This model is meant to work with stem density.
 * @author Mathieu Fortin - April 2024
 */
class ModifiedChapmanRichardsDerivativeModelWithRandomEffectImplementation	extends AbstractMixedModelFullImplementation {

	ModifiedChapmanRichardsDerivativeModelWithRandomEffectImplementation(String outputType, MetaModel model, LinkedHashMap<String, Object>[] startingValues) throws StatisticalDataException {
		super(outputType, model, startingValues);
	}
	


	@Override
	double getPrediction(double ageYr, double timeSinceBeginning, double r1, Matrix parameters) {
		return ModifiedChapmanRichardsDerivativeModelImplementation.computePredictions(
				parameters == null ? getParameters() : parameters, 
				ageYr, 
				timeSinceBeginning, 
				r1);
	}

	@Override
	Matrix getFirstDerivative(double ageYr, double timeSinceBeginning, double r1) {
		return ModifiedChapmanRichardsDerivativeModelImplementation.computeDerivatives(getParameters(), ageYr, timeSinceBeginning, r1);
	}

	@Override
	public boolean isInterceptModel() {return false;}

	@Override
	public List<String> getEffectList() {
		return Arrays.asList(new String[] {"b1","b2","b3"});
	}

	@Override
	List<String> getParameterNames() {
		if (parameterIndexMap == null) {
			fixedEffectsParameterIndices = new ArrayList<Integer>();
			fixedEffectsParameterIndices.add(0);
			fixedEffectsParameterIndices.add(1);
			fixedEffectsParameterIndices.add(2);
			parameterIndexMap = new LinkedHashMap<String, Integer>();
			int lastIndex = 0;
			parameterIndexMap.put("b1", lastIndex++);
			parameterIndexMap.put("b2", lastIndex++);
			parameterIndexMap.put("b3", lastIndex++);
			parameterIndexMap.put(CORRELATION_PARM, lastIndex++);
			parameterIndexMap.put(RANDOM_EFFECT_STD, lastIndex++);
			if (!isVarianceErrorTermAvailable) {
				parameterIndexMap.put(RESIDUAL_VARIANCE, lastIndex++);
			}
			if (isRegenerationLagEvaluationNeeded) {
				parameterIndexMap.put(REG_LAG_PARM, lastIndex++);
			}
			Set<String> names = parameterIndexMap.keySet();
			parameterNames = Arrays.asList(names.toArray(new String[] {}));
		}
		return parameterNames;
	}


	@Override
	public String getModelDefinition() {
		return "y ~ (b1 + u_i)*exp(-b2*t)*(1-exp(-b3*t))";
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
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.005 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.0001", "0.01"});
		 inputMap[1] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b3");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.2 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.001", "0.5"});
		 inputMap[2] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractModelImplementation.CORRELATION_PARM);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.92 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.80", "0.995"});
		 inputMap[3] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 500 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "2000"});
		 inputMap[4] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), AbstractModelImplementation.RESIDUAL_VARIANCE);
		 oMap.put(InputParametersMapKey.StartingValue.name(), 2500 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "5000"});
		 inputMap[5] = oMap;
		 return inputMap;
	}}
