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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Stores and manipulates metadata associated with the metamodel.   
 * @author Jean-Francois Lavoie and Mathieu Fortin - September 2021
 */
public class MetaModelMetaData {
			
	private static SimpleDateFormat DATE_FORMAT_OLD = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	private static SimpleDateFormat DATE_FORMAT_NEW = new SimpleDateFormat("EEEE, MMMMM d, yyyy");
	
	public class Growth {
		public String geoDomain;	// ex : QC_FMU02664
		public String dataSource;	// which inventory was used ? "4th Campaign of the province forest inventory"
		public TreeMap<Integer, List<Integer>> dataSourceYears; 
		public int nbRealizations; 
		public String climateChangeOption;	
		public String growthModel;	
		public TreeMap<Integer, String> upscaling;	
		public LinkedHashMap<Integer, Integer> nbPlots;	 
		
		public Growth() {
			nbPlots = new LinkedHashMap<Integer, Integer>();
			dataSourceYears = new TreeMap<Integer, List<Integer>>();
			upscaling = new TreeMap<Integer, String>();
		}
	}
	
	public class Fit {
		public Date timeStamp;	// the datetime at which the MM was fitted
		public String outputType;	
		public String fitModel; 	
		public String stratumGroup;		
		
		public Fit() {			
		}
	}		
	
	public final Growth growth;
	public final Fit fit;

	/**
	 * Constructor.
	 */
	public MetaModelMetaData () {	
		this.growth = new MetaModelMetaData.Growth();
		this.fit = new MetaModelMetaData.Fit();
	}	

	/**
	 * Produce a MetaModelMetaData from a JSON LinkedHashMap.
	 * @param map a LinkedHashMap instance coming from the reading of a JSON file.
	 * @return a MetaModelMetaData instance
	 * @throws ParseException if the date cannot be properly parsed
	 */
	@SuppressWarnings("rawtypes")
	public static MetaModelMetaData deserializeFromJSONLinkedHashMap(LinkedHashMap map) throws ParseException {
		boolean formerJSONIOImplementation = map.containsKey("@id");
		MetaModelMetaData metaData = new MetaModelMetaData();
		Map growthMap = (Map) map.get("growth");
		metaData.growth.geoDomain = growthMap.get("geoDomain").toString();
		metaData.growth.dataSource = growthMap.get("dataSource").toString();
		metaData.growth.dataSourceYears =  parseDateSourceYears((LinkedHashMap) growthMap.get("dataSourceYears"), formerJSONIOImplementation);
		metaData.growth.nbRealizations = Integer.parseInt(growthMap.get("nbRealizations").toString());
		metaData.growth.climateChangeOption = growthMap.get("climateChangeOption").toString();
		metaData.growth.growthModel = growthMap.get("growthModel").toString();
		metaData.growth.upscaling = parseUpscaling((LinkedHashMap) growthMap.get("upscaling"), formerJSONIOImplementation);
		metaData.growth.nbPlots = parseNbPlots((LinkedHashMap) growthMap.get("nbPlots"), formerJSONIOImplementation);
		Map fitMap = (Map) map.get("fit");
		if (fitMap.containsKey("timeStamp")) {
			String dateString = fitMap.get("timeStamp").toString();
			Date d = formerJSONIOImplementation ?
					DATE_FORMAT_OLD.parse(dateString) :
						DATE_FORMAT_NEW.parse(dateString);
			metaData.fit.timeStamp = d;
		}
		metaData.fit.fitModel = (String) fitMap.get("fitModel");
		metaData.fit.outputType = (String) fitMap.get("outputType");
		metaData.fit.stratumGroup = (String) fitMap.get("stratumGroup");
		return metaData;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static TreeMap<Integer, List<Integer>> parseDateSourceYears(LinkedHashMap dataSourceYearsMap, boolean formerJSONIOImplementation) {
		TreeMap<Integer, List<Integer>> outputMap = new TreeMap<Integer, List<Integer>>();
		if (formerJSONIOImplementation) {
			List keys = (List) dataSourceYearsMap.get("@keys");
			List values = (List) dataSourceYearsMap.get("@items");
			for (int i = 0; i < keys.size(); i++) {
				int k = (Integer) ((LinkedHashMap) keys.get(i)).get("value");
				List<LinkedHashMap> valueList = (List) ((LinkedHashMap) values.get(i)).get("@items");
				List<Integer> valuesForThisKey = new ArrayList<Integer>();
				for (LinkedHashMap o : valueList) {
					valuesForThisKey.add((Integer) o.get("value"));
				}
				outputMap.put(k, valuesForThisKey);
			}
			return outputMap;
		} else {
			for (Object k : dataSourceYearsMap.keySet()) {
				outputMap.put(Integer.parseInt(k.toString()), (List) dataSourceYearsMap.get(k));
			}
			return outputMap;
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static TreeMap<Integer, String> parseUpscaling(LinkedHashMap upscalingMap, boolean formerJSONIOImplementation) {
		TreeMap<Integer, String> outputMap = new TreeMap<Integer, String>();
		if (formerJSONIOImplementation) {
			List keys = (List) upscalingMap.get("@keys");
			List<String> values = (List) upscalingMap.get("@items");
			for (int i = 0; i < keys.size(); i++) {
				int k = (Integer) ((LinkedHashMap) keys.get(i)).get("value");
				outputMap.put(k, values.get(i));
			}
			return outputMap;
		} else {
			for (Object k : upscalingMap.keySet()) {
				outputMap.put(Integer.parseInt(k.toString()), upscalingMap.get(k).toString());
			}
			return outputMap;
		}
	}

	
	@SuppressWarnings( "rawtypes" )
	private static LinkedHashMap<Integer, Integer> parseNbPlots(LinkedHashMap nbPlotsMap, boolean formerJSONIOImplementation) {
		LinkedHashMap<Integer, Integer> outputMap = new LinkedHashMap<Integer, Integer>();
		if (formerJSONIOImplementation) {
			List keys = (List) nbPlotsMap.get("@keys");
			List values = (List) nbPlotsMap.get("@items");
			for (int i = 0; i < keys.size(); i++) {
				int k = (Integer) ((LinkedHashMap) keys.get(i)).get("value");
				int value = (Integer) ((LinkedHashMap) values.get(i)).get("value");
				outputMap.put(k, value);
			}
			return outputMap;
		} else {
			for (Object k : nbPlotsMap.keySet()) {
				outputMap.put(Integer.parseInt(k.toString()), (Integer) nbPlotsMap.get(k));
			}
			return outputMap;
		}
	}


}
