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
import java.util.Collections;
import java.util.List;

import repicea.math.DiagonalMatrix;
import repicea.math.Matrix;
import repicea.simulation.climate.REpiceaClimateGenerator.ClimateChangeScenario;
import repicea.stats.data.DataSet;
import repicea.stats.data.Observation;

/**
 * Handle the result of a simulation performed through the ExtScript class
 * @author Mathieu Fortin - January 2021
 */
public class ScriptResult {	
	
	public static final String DateYrFieldName = "DateYr";	
	public static final String TimeSinceInitialDateYrFieldName = "timeSinceInitialDateYr";
	public static final String OutputTypeFieldName = "OutputType";
	public static final String EstimateFieldName = "Estimate";
	public static final String TotalVarianceFieldName = "TotalVariance";
	public static final String VarianceEstimatorType = "VarianceEstimatorType";	

	final DataSet dataset;
	final List<String> outputTypes;
	final int nbRealizations;
	final int nbPlots;
	final ClimateChangeScenario climateChangeScenario;
	final String growthModel;
	
	/**
	 * Constructor.
	 * @param nbRealizations the number of realizations (0 if deterministic or >0 if stochastic)
	 * @param nbPlots the number of plots used in the projection
	 * @param climateChangeScenario a ClimateChangeScenario enum
	 * @param growthModel the model name
	 * @param dataset a DataSet instance containing the projection for a group of plots
	 */
	public ScriptResult(int nbRealizations, 
			int nbPlots,
			ClimateChangeScenario climateChangeScenario,
			String growthModel,
			DataSet dataset) {
		this.nbRealizations = nbRealizations;
		this.nbPlots = nbPlots;
		this.dataset = dataset;
		this.climateChangeScenario = climateChangeScenario;
		this.growthModel = growthModel;
		
		List<Integer> sortingIndex = new ArrayList<Integer>();
		int outputTypeFieldNameIndex = getDataSet().getFieldNames().indexOf(OutputTypeFieldName);
		sortingIndex.add(outputTypeFieldNameIndex);
		sortingIndex.add(getDataSet().getFieldNames().indexOf(DateYrFieldName));
		getDataSet().sortObservations(sortingIndex);	// the dataset is sorted according to the output type and then the date yr
		
		outputTypes = new ArrayList<String>();
		for (Observation o : getDataSet().getObservations()) {
			String outputType =  o.toArray()[outputTypeFieldNameIndex].toString();
			if (!outputTypes.contains(outputType)) {
				outputTypes.add(outputType);
			}
		}
		Collections.sort(outputTypes);
	}

	/**
	 * Return an empty data set already formatted with the appropriate field names. <br>
	 * <br>
	 * This one is meant for stochastic model projections which include variance estimates.
	 * @return a DataSet instance
	 */
	public static DataSet createEmptyDataSet() {
		return new DataSet(Arrays.asList(new String[] {DateYrFieldName,
				TimeSinceInitialDateYrFieldName,
				OutputTypeFieldName, 
				EstimateFieldName, 
				TotalVarianceFieldName,
				VarianceEstimatorType}));
	}

	/**
	 * Return an empty data set already formatted with the appropriate field names. <br>
	 * <br>
	 * This one does not contain any variance field.
	 * @return a DataSet instance
	 */
	public static DataSet createEmptyReducedDataSet() {
		return new DataSet(Arrays.asList(new String[] {DateYrFieldName,
				TimeSinceInitialDateYrFieldName,
				OutputTypeFieldName, 
				EstimateFieldName}));
	}

	public int getNbRealizations() {return nbRealizations;}
	
	public ClimateChangeScenario getClimateChangeScenario() {return climateChangeScenario;}
	
	public String getGrowthModel() {return growthModel;}
	
	public DataSet getDataSet() {return dataset;}
	
	public List<String> getOutputTypes() {return outputTypes;}

	/**
	 * Check if the variance field is contained in the DataSet instance.
	 * @return true if the variance is in or false otherwise
	 */
	boolean isVarianceAvailable() {
		return getDataSet().getFieldNames().indexOf(TotalVarianceFieldName) > -1;
	}
	
	/**
	 * Sort the dataset and create the variance-covariance matrix of the error term.
	 * <br>
	 * The current implementation assumes the variance-covariance matrix is a diagonal matrix.
	 * If the DataSet instance does not have a variance field, it returns null.
	 * @param outputType a string defining the output type we are interested in (e.g. volume_alive_allspecies)
	 * @return a Matrix
	 */
	protected Matrix computeVarCovErrorTerm(String outputType) {
		if (isVarianceAvailable()) {
			int outputFieldTypeIndex = getDataSet().getFieldNames().indexOf(OutputTypeFieldName);
			List<Observation> selectedObservations = new ArrayList<Observation>();
			for (Observation o : getDataSet().getObservations()) {
				if (o.toArray()[outputFieldTypeIndex].equals(outputType)) {
					selectedObservations.add(o);
				}
			}
			int nbObs = selectedObservations.size();
			int varianceIndex = getDataSet().getFieldNames().indexOf(TotalVarianceFieldName);
			DiagonalMatrix mat = new DiagonalMatrix(nbObs);
			for (int i = 0; i < nbObs; i++) {
				mat.setValueAt(i, i, (Double) selectedObservations.get(i).toArray()[varianceIndex]);
			}
			return mat;
		} else {
			return  null;
		}
	}
	
	/**
	 * Check the compatibility between two ScriptResult instances. To be compatible,
	 * the DataSet instances nested in the ScriptResult instances must share the 
	 * same field names and the same field types. Moreover the ScriptResult instances 
	 * must have the same output types. 
	 * @param result a ScriptResult instance
	 * @return a boolean
	 */
	protected boolean isCompatible(ScriptResult result) {
		return (dataset.getFieldNames().equals(result.dataset.getFieldNames()) && 
				dataset.getFieldTypes().equals(result.dataset.getFieldTypes()) &&
				outputTypes.equals(result.outputTypes) && 
				this.nbRealizations == result.nbRealizations &&
				this.climateChangeScenario.equals(result.climateChangeScenario) &&
				this.growthModel.equals(result.growthModel));
	}
}
