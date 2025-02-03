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

import java.lang.reflect.Array;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import repicea.stats.distributions.ContinuousDistribution;
import repicea.stats.distributions.UniformDistribution;

/**
 * A class to handle JSON formatted parameters for meta model implementations.
 * @author Mathieu Fortin - April 2024
 */
public class ParametersMapUtilities {

	/**
	 * An enum to define the keys of the LinkedHashMap provided by JSON 
	 * deserialization.
	 * Current values are:
	 * <ul>
	 * <li> <b>Parameter</b>: the parameter name (string)
	 * <li> <b>StartingValue</b>: the starting value (double)
	 * <li> <b>Distribution</b>: the name of the distribution (e.g. Uniform)
	 * <li> <b>DistParms</b>: a List of doubles standing for the distribution parameters
	 * </ul>
	 */
	public static enum InputParametersMapKey {
		Parameter(String.class),
		StartingValue(Double.class),
		Distribution(String.class),
		DistParms(List.class);
		
		final Class<?> expectedClass;
		
		InputParametersMapKey(Class<?> expectedClass) {
			this.expectedClass = expectedClass;
		}
	}

	static enum FormattedParametersMapKey {
		StartingValue,
		PriorDistribution;
	}

	@SuppressWarnings("rawtypes")
	private static Map<FormattedParametersMapKey, Object> checkClonedMapMapValidity(Map<String, Object> clonedMap) {
		Map<FormattedParametersMapKey, Object> formattedInnerMap = new HashMap<FormattedParametersMapKey, Object>(); 
		for (InputParametersMapKey key : InputParametersMapKey.values()) {
			if (!clonedMap.containsKey(key.name()) ) {
				throw new InvalidParameterException("The parameter map should contain key: " + key.name() + " !");
			}
			Object value = clonedMap.get(key.name());
			if (!key.expectedClass.isAssignableFrom(value.getClass()) || key == InputParametersMapKey.DistParms) { // distParms is a list but its element are not formatted
				if (key.expectedClass.equals(String.class)) {
					clonedMap.put(key.name(), value.toString());
				} else if (key.expectedClass.equals(Double.class)) {
					clonedMap.put(key.name(), Double.parseDouble(value.toString()));
				} else if (key.expectedClass.equals(List.class)) {
					boolean isArray = value.getClass().isArray();
					if (value instanceof List || isArray) {
						int arrayLength = isArray ?
								Array.getLength(value) :
									((List) value).size();
						Double[] doubleArray = new Double[arrayLength];
						for (int j = 0; j < arrayLength; j++) {
							doubleArray[j] = Double.parseDouble(isArray ? 
									Array.get(value, j).toString() : 
										((List) value).get(j).toString());
						}
						clonedMap.put(key.name(), doubleArray);
					} else {
						clonedMap.put(key.name(), new Double[] {Double.parseDouble(value.toString())});
					}
				}
			}
		}
		formattedInnerMap.put(FormattedParametersMapKey.StartingValue, (Double) clonedMap.get(InputParametersMapKey.StartingValue.name()));
		String distributionName = (String) clonedMap.get(InputParametersMapKey.Distribution.name());
		Double[] distributionParms = (Double[]) clonedMap.get(InputParametersMapKey.DistParms.name());
		formattedInnerMap.put(FormattedParametersMapKey.PriorDistribution, createDistribution(distributionName, distributionParms));
		return formattedInnerMap;
	}

	
	private static ContinuousDistribution createDistribution(String distributionName, Double[] distributionParms) {
		switch(distributionName) {
		case "Uniform":
			return new UniformDistribution(distributionParms[0], distributionParms[1]);
		default:
			throw new InvalidParameterException("The distribution " + distributionName + " is not supported yet!"); 
		}
	}

	/**
	 * Provide a formatted parameter map from the unformatted JSON based map.
	 * @param unformattedMap a LinkedHashMap instance from a JSON string
	 * @param parametersForThisModel the list of parameters to be provided for a particular model implementation
	 * @return a Map of Map instance key1 is the parameter name, key2 refers to the starting value and  prior distribution.
	 */
	static Map<String, Map<FormattedParametersMapKey, Object>> formatParametersMap(Map<String, Object>[] unformattedMap, List<String> parametersForThisModel, List<String> nuisanceParms) {
		Map<String, Map<FormattedParametersMapKey, Object>> parameterMap = new HashMap<String, Map<FormattedParametersMapKey, Object>>();
		for (Map<String, Object> entry : unformattedMap) {
			Map<String, Object> clonedMap = new HashMap<String, Object>();
			clonedMap.putAll(entry);
			Map<FormattedParametersMapKey, Object> formattedMap = checkClonedMapMapValidity(clonedMap);
			String parameterName = clonedMap.remove("Parameter").toString();
			parameterMap.put(parameterName, formattedMap);
		}
		for (String p : parametersForThisModel) {
			if (!parameterMap.containsKey(p) && !nuisanceParms.contains(p)) {
				throw new InvalidParameterException("Parameter " + p + " is not included in the parameter map!");
			}
		}
		return parameterMap;
	}
	

}
