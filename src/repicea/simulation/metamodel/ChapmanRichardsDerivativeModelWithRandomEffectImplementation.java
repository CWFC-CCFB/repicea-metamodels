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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import repicea.math.Matrix;
import repicea.stats.data.StatisticalDataException;
import repicea.stats.distributions.ContinuousDistribution;
import repicea.stats.distributions.GaussianDistribution;
import repicea.stats.distributions.UniformDistribution;
import repicea.stats.estimators.mcmc.MetropolisHastingsPriorHandler;

/**
 * An implementation of the derivative form of the Chapman-Richards model including random effects.
 * @author Mathieu Fortin - October 2021
 */
class ChapmanRichardsDerivativeModelWithRandomEffectImplementation extends AbstractMixedModelFullImplementation {
	
	ChapmanRichardsDerivativeModelWithRandomEffectImplementation(String outputType, MetaModel model) throws StatisticalDataException {
		super(outputType, model);
	}

	
	@Override
	public GaussianDistribution getStartingParmEst(double coefVar) {
		indexCorrelationParameter = 3;
		indexRandomEffectVariance = 4;
		if (!isVarianceErrorTermAvailable) {
			indexResidualErrorVariance = 5;
			indexFirstRandomEffect = 6;
		} else {
			indexResidualErrorVariance = -1;
			indexFirstRandomEffect = 5;
		}
		
		Matrix parmEst = new Matrix(indexFirstRandomEffect + dataBlockWrappers.size(),1);
		parmEst.setValueAt(0, 0, 1000d);
		parmEst.setValueAt(1, 0, 0.02);
		parmEst.setValueAt(2, 0, 2d);
		parmEst.setValueAt(indexCorrelationParameter, 0, .92);
		parmEst.setValueAt(indexRandomEffectVariance, 0, 1000d);
		if (!isVarianceErrorTermAvailable) {
			parmEst.setValueAt(indexResidualErrorVariance, 0, 250d);
		}
		for (int i = 0; i < dataBlockWrappers.size(); i++) {
			parmEst.setValueAt(indexFirstRandomEffect + i, 0, 2 * Math.sqrt(parmEst.getValueAt(indexRandomEffectVariance, 0))); // 2 stands for the 97.5th percentile
		}
		
		fixedEffectsParameterIndices = new ArrayList<Integer>();
		fixedEffectsParameterIndices.add(0);
		fixedEffectsParameterIndices.add(1);
		fixedEffectsParameterIndices.add(2);

		Matrix varianceDiag = new Matrix(parmEst.m_iRows,1);
		for (int i = 0; i < varianceDiag.m_iRows; i++) {
			varianceDiag.setValueAt(i, 0, Math.pow(parmEst.getValueAt(i, 0) * coefVar, 2d));
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
	public List<String> getOtherParameterNames() {
		List<String> parameters = new ArrayList<String>();
		parameters.add("rho");
		parameters.add("sigma2_u");
		if (!isVarianceErrorTermAvailable)
			parameters.add("sigma2_res");
		int nbRandomEffects = mh.getFinalParameterEstimates().m_iRows - getEffectList().size() - parameters.size();
		for (int i = 1; i <= nbRandomEffects; i++) {
			parameters.add("u_" + i);
		}
		return parameters;
	}

	@Override
	public String getModelDefinition() {
		return "y ~ (b1 + u_i)*exp(-b2*t)*(1-exp(-b2*t))^b3";
	}


	@Override
	public void setPriorDistributions(MetropolisHastingsPriorHandler handler) {
		handler.clear();
		handler.addFixedEffectDistribution(new UniformDistribution(0, 2000), 0);
		handler.addFixedEffectDistribution(new UniformDistribution(0.00001, 0.05), 1);
		handler.addFixedEffectDistribution(new UniformDistribution(0.8, 6), 2);
		handler.addFixedEffectDistribution(new UniformDistribution(0.80, 0.995), indexCorrelationParameter);
		ContinuousDistribution variancePrior = new UniformDistribution(0, 15000);
		handler.addFixedEffectDistribution(variancePrior, indexRandomEffectVariance);
		if (!isVarianceErrorTermAvailable) {
			ContinuousDistribution resVariancePrior = new UniformDistribution(0, 5000);
			handler.addFixedEffectDistribution(resVariancePrior, indexResidualErrorVariance);
		}
		for (int i = 0; i < dataBlockWrappers.size(); i++) {
			handler.addRandomEffectVariance(new GaussianDistribution(0, 1), variancePrior, indexFirstRandomEffect + i);
		}
	}

}
