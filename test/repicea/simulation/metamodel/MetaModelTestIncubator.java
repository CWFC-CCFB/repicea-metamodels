/*
 * This file is part of the repicea-metamodel library.
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

import java.io.IOException;
import java.util.LinkedHashMap;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import repicea.simulation.metamodel.MetaModel.ModelImplEnum;
import repicea.util.ObjectUtility;

/**
 * A class for incubating tests before moving them to another class.
 * @author Mathieu Fortin - September 2024
 */
public class MetaModelTestIncubator {

	@SuppressWarnings("unchecked")
	@Test
	public void testXXStemDensityWithModifiedFourParameterChapmanRichardsDerivativeModel() throws IOException {
		String filename = ObjectUtility.getPackagePath(getClass()) + "QC_2EST_MJ22_NoChange.zml";
		MetaModel m = MetaModel.Load(filename);
		m.getMetropolisHastingsParameters().nbBurnIn = 5000;
		m.getMetropolisHastingsParameters().nbAcceptedRealizations = 15000;
		m.getMetropolisHastingsParameters().nbInitialGrid = 10000;
		m.getMetropolisHastingsParameters().oneEach = 25;

		LinkedHashMap<String, Object> startingValuesMap = new LinkedHashMap<String, Object>();
		
		LinkedHashMap<String, Object>[] parms = new LinkedHashMap[5];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "2500", "Uniform", new String[] {"0", "5000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.005", "Uniform", new String[] {"0.0001", "0.01"}});
		parms[2] = MetaModel.convertParameters(new Object[] {"b3", "0.2", "Uniform", new String[] {"0.001", "0.5"}});
		parms[3] = MetaModel.convertParameters(new Object[] {"b4", "1", "Uniform", new String[] {"0.8", "6"}});
		parms[4] = MetaModel.convertParameters(new Object[] {AbstractModelImplementation.CORRELATION_PARM, "0.92", "Uniform", new String[] {"0.8", "0.995"}});

//		Map<String, Object> args = new HashMap<String, Object>();
//		args.put(JsonWriter.TYPE, false);
//		String jsonStr = JsonWriter.objectToJson(parms, args);
//		String jsonStr = JsonIo.toJson(parms, new WriteOptionsBuilder().showTypeInfoNever().build());
		ObjectMapper om = new ObjectMapper();
		String jsonStr = om.writeValueAsString(parms);
		startingValuesMap.put(ModelImplEnum.ModifiedChapmanRichardsFourParameterDerivative.name(), jsonStr);
	
		parms = new LinkedHashMap[6];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "2500", "Uniform", new String[] {"0", "5000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.005", "Uniform", new String[] {"0.0001", "0.01"}});
		parms[2] = MetaModel.convertParameters(new Object[] {"b3", "0.2", "Uniform", new String[] {"0.001", "0.5"}});
		parms[3] = MetaModel.convertParameters(new Object[] {"b4", "1", "Uniform", new String[] {"0.8", "6"}});
		parms[4] = MetaModel.convertParameters(new Object[] {AbstractModelImplementation.CORRELATION_PARM, "0.92", "Uniform", new String[] {"0.8", "0.995"}});
		parms[5] = MetaModel.convertParameters(new Object[] {AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD, "100", "Uniform", new String[] {"1", "200"}});

		jsonStr = om.writeValueAsString(parms);
		startingValuesMap.put(ModelImplEnum.ModifiedChapmanRichardsFourParameterDerivativeWithRandomEffect.name(), jsonStr);
		
		m.fitModel("AliveStemDensity_AllSpecies", startingValuesMap);
		Assert.assertTrue("Check if regeneration lag was enabled", m.model.isRegenerationLagEvaluationNeeded);
		System.out.println(m.getModelComparison());
		System.out.println(m.getSummary());
		Assert.assertTrue("Testing the selection of the best model.", m.getSummary().contains("ModifiedChapmanRichardsFourParameterDerivativeModelImplementation"));
		double b1 = m.getFinalParameterEstimates().getValueAt(0, 0);
		Assert.assertEquals("Testing parameter b1", 1300, b1, 250);
	}
	
}
