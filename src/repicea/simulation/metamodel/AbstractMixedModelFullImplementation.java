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
import repicea.stats.data.StatisticalDataException;
import repicea.stats.distributions.GaussianDistribution;

/**
 * The AbstractMixedModelImplementation class relies on a marginal log-likelihood instead of a pure 
 * log-likelihood.
 * @author Mathieu Fortin - October 2021
 */
abstract class AbstractMixedModelFullImplementation extends AbstractModelImplementation {
	
	protected static final String RANDOM_EFFECT_STD = "sigma_u";
	
	int indexRandomEffectStandardDeviation;
	int indexFirstRandomEffect;
	
	GaussianDistribution randomEffectDistribution;
	
	AbstractMixedModelFullImplementation(String outputType, MetaModel metaModel, LinkedHashMap<String, Object>[] startingValues) throws StatisticalDataException {
		super(outputType, metaModel, startingValues);
	}

	@Override
	protected double getLogLikelihoodForThisBlock(Matrix parameters, int i) {
		AbstractDataBlockWrapper dbw = dataBlockWrappers.get(i);
		double randomEffect = parameters.getValueAt(indexFirstRandomEffect + i, 0);
		dbw.setParameterValue(0, randomEffect);
		return dbw.getLogLikelihood();
	}	
	
	protected double getVarianceDueToRandomEffect(double ageYr, double timeSinceBeginning) {		
		double randomEffectStandardDeviation = getParameters().getValueAt(indexRandomEffectStandardDeviation, 0);
		double value = this.getFirstDerivative(ageYr, timeSinceBeginning, 0.0).getValueAt(0, 0);
		
		return value * value * randomEffectStandardDeviation * randomEffectStandardDeviation;   
	}

	@Override
	public final List<String> getOtherParameterNames() {
		List<String> parameters = new ArrayList<String>();
		parameters.add(AbstractModelImplementation.CORRELATION_PARM);
		parameters.add(AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD);
		if (!isVarianceErrorTermAvailable)
			parameters.add(AbstractModelImplementation.RESIDUAL_VARIANCE);
		for (AbstractDataBlockWrapper w : dataBlockWrappers) {
			parameters.add("u_" + w.blockId.substring(0, w.blockId.indexOf("_")));
		}
		return parameters;
	}

		
}
