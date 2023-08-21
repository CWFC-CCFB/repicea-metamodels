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

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;


/**
 * Stores and manipulates metadata associated with the metamodel.   
 * @author Jean-Francois Lavoie and Mathieu Fortin - September 2021
 */
public class MetaModelMetaData {
			
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
	
	public Growth growth;
	public Fit fit;
	
	public MetaModelMetaData () {	
		this.growth = new MetaModelMetaData.Growth();
		this.fit = new MetaModelMetaData.Fit();
	}	
}
