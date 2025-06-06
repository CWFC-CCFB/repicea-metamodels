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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Stores and manipulates metadata associated with the metamodel.   
 * @author Jean-Francois Lavoie and Mathieu Fortin - September 2021
 */
public class MetaModelMetaData {
			
	private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static SimpleDateFormat DATE_FORMAT_OLD = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	private static List<DateFormat> DATE_FORMATS = new ArrayList<DateFormat>();
	static {
		DATE_FORMATS.add(DateFormat.getDateInstance(DateFormat.FULL, Locale.ENGLISH));
		DATE_FORMATS.add(DateFormat.getDateInstance(DateFormat.FULL, Locale.FRENCH));
	}
	
	public class Growth {
		public String geoRegion;  	// ex : QC
		public String geoDomain;	// ex : 2OUEST
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
		public String leadingSpecies;
		
		public Fit() {}
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

	
	private static Date parseDate(String dateStr) {
		Date d = null;
		for (DateFormat dateFormat : DATE_FORMATS) {
			try {
				d = dateFormat.parse(dateStr);
				break;
			} catch (ParseException e) {}
		}
		if (d == null) {
			throw new UnsupportedOperationException("Unable to parse date " + dateStr);
		} else {
			return d;
		}
	}
	
	/**
	 * Produce a MetaModelMetaData from a JSON LinkedHashMap.
	 * @param jsonFilename the path to a JSON file.
	 * @return a MetaModelMetaData instance or null is something went wrong
	 */
	@SuppressWarnings("rawtypes")
	public static MetaModelMetaData deserializeFromJSONFile(String jsonFilename) {
		FileInputStream is = null;
		try {
	        is = new FileInputStream(jsonFilename);
	        LinkedHashMap jsonMap = OBJECT_MAPPER.readValue(is, LinkedHashMap.class);
			boolean formerJSONIOImplementation = jsonMap.containsKey("@id");
			MetaModelMetaData metaData = new MetaModelMetaData();
			if (!jsonMap.containsKey("growth")) { // the JSON does not contain a meta-data instance.
				return null;
			} else {
				Map growthMap = (Map) jsonMap.get("growth");
				// Make sure the geoRegion is always extracted from filename
				int index = jsonFilename.lastIndexOf(File.separator);
				if (index == -1) {
					index = jsonFilename.lastIndexOf("/");
				}
				String simpleFilename = index != -1 ?
						jsonFilename.substring(index + 1) :
							jsonFilename;
				metaData.growth.geoRegion = simpleFilename.substring(0,simpleFilename.indexOf("_"));
				
				metaData.growth.geoDomain = growthMap.get("geoDomain").toString();
				metaData.growth.dataSource = growthMap.get("dataSource").toString();
				metaData.growth.dataSourceYears =  parseDateSourceYears((LinkedHashMap) growthMap.get("dataSourceYears"), formerJSONIOImplementation);
				metaData.growth.nbRealizations = Integer.parseInt(growthMap.get("nbRealizations").toString());
				metaData.growth.climateChangeOption = growthMap.get("climateChangeOption").toString();
				metaData.growth.growthModel = growthMap.get("growthModel").toString();
				metaData.growth.upscaling = parseUpscaling((LinkedHashMap) growthMap.get("upscaling"), formerJSONIOImplementation);
				metaData.growth.nbPlots = parseNbPlots((LinkedHashMap) growthMap.get("nbPlots"), formerJSONIOImplementation);
				Map fitMap = (Map) jsonMap.get("fit");
				if (fitMap.containsKey("timeStamp")) {
					String dateString = fitMap.get("timeStamp").toString();
					Date d = formerJSONIOImplementation ?
							DATE_FORMAT_OLD.parse(dateString) :
								parseDate(dateString);
					metaData.fit.timeStamp = d;
				}
				metaData.fit.fitModel = (String) fitMap.get("fitModel");
				metaData.fit.outputType = (String) fitMap.get("outputType");
				metaData.fit.stratumGroup = (String) fitMap.get("stratumGroup");
				// make sure leading species is always extracted from filename
				if (simpleFilename.contains("-")) {
					int indexLeading = simpleFilename.indexOf("-") + 1;
					metaData.fit.leadingSpecies = simpleFilename.substring(indexLeading, simpleFilename.indexOf("_", indexLeading));
				} else {
					metaData.fit.leadingSpecies = "None";
				}

				return metaData;
			}
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		} finally { 
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {}
			}
		}
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
