/*
 * This file is part of the repicea-metamodels library.
 *
 * Copyright (C) 2024 His Majesty the King in right of Canada
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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import repicea.simulation.metamodel.ParametersMapUtilities.FormattedParametersMapKey;
import repicea.simulation.metamodel.ParametersMapUtilities.InputParametersMapKey;
import repicea.stats.distributions.UniformDistribution;

public class ParametersMapUtilitiesTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testMapFormatting() {
		 LinkedHashMap<String, Object>[] inputMap = new LinkedHashMap[5];
		 LinkedHashMap<String, Object> oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b1");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 100 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "400"});
		 inputMap[0] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b2");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.02 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.0001", "0.1"});
		 inputMap[1] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "b3");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 2 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"1", "6"});
		 inputMap[2] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "rho");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 0.92 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0.80", "0.995"});
		 inputMap[3] = oMap;
		 oMap = new LinkedHashMap<String, Object>();
		 oMap.put(InputParametersMapKey.Parameter.name(), "sigma2_res");
		 oMap.put(InputParametersMapKey.StartingValue.name(), 250 + "");
		 oMap.put(InputParametersMapKey.Distribution.name(), "Uniform");
		 oMap.put(InputParametersMapKey.DistParms.name(), new String[]{"0", "5000"});
		 inputMap[4] = oMap;

		List<String> parameterNames = Arrays.asList(new String[] {"b1", "b2", "b3", AbstractModelImplementation.CORRELATION_PARM, AbstractModelImplementation.RESIDUAL_VARIANCE});
		Map<String, Map<FormattedParametersMapKey, Object>> result = ParametersMapUtilities.formatParametersMap(inputMap, parameterNames, AbstractModelImplementation.NUISANCE_PARMS);
		Assert.assertEquals("Testing map size", 5, result.size());
		Assert.assertEquals("Testing starting value", 0.02, (Double) result.get("b2").get(FormattedParametersMapKey.StartingValue), 1E-8);
		UniformDistribution d = (UniformDistribution) result.get("b3").get(FormattedParametersMapKey.PriorDistribution);
		Assert.assertEquals("Testing uniform distribution lower bound", 1d, d.getLowerBound().getBoundValue().getValueAt(0, 0), 1E-8);
		Assert.assertEquals("Testing uniform distribution upper bound", 6d, d.getUpperBound().getBoundValue().getValueAt(0, 0), 1E-8);
	}
}
