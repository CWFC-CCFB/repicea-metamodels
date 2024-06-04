/*
 * This file is part of the repicea-metamodel library.
 *
 * Copyright (C) 2009-2019 Mathieu Fortin for Rouge Epicea.
 * Copyright (C) 2020-2024 His Majesty the King in Right of Canada
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.cedarsoftware.util.io.JsonWriter;

import repicea.math.Matrix;
import repicea.serial.SerializerChangeMonitor;
import repicea.simulation.climate.REpiceaClimateGenerator.RepresentativeConcentrationPathway;
import repicea.simulation.metamodel.MetaModel.ModelImplEnum;
import repicea.simulation.scriptapi.ScriptResult;
import repicea.stats.data.DataSet;
import repicea.util.ObjectUtility;
import repicea.util.REpiceaLogManager;
import repicea.util.REpiceaTranslator;
import repicea.util.REpiceaTranslator.Language;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MetaModelTest {

	static {		
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelManager", 
				"repicea.simulation.metamodel.MetaModelManager");
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel", 
				"repicea.simulation.metamodel.MetaModel");
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.DataBlockWrapper", 
				"repicea.simulation.metamodel.DataBlockWrapper");		
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelGibbsSample", 
				"repicea.simulation.metamodel.MetaModelGibbsSample");
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$Bound", 
				"repicea.simulation.metamodel.MetaModel$Bound");
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$InnerModel", 
				"repicea.simulation.metamodel.MetaModel$InnerModel");		
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.ExtScriptResult", 
				"repicea.simulation.metamodel.ScriptResult");				
		SerializerChangeMonitor.registerClassNameChange("repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation", 
				"repicea.simulation.metamodel.ChapmanRichardsModelWithRandomEffectImplementation");				
		SerializerChangeMonitor.registerClassNameChange("repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation$DataBlockWrapper", 
				"repicea.simulation.metamodel.ChapmanRichardsModelWithRandomEffectImplementation$DataBlockWrapper");				
		SerializerChangeMonitor.registerEnumNameChange("repicea.simulation.metamodel.MetaModel$ModelImplEnum", 
				"RichardsChapmanWithRandomEffect", "ChapmanRichardsWithRandomEffect");
		
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.GaussHermiteQuadrature", 
				"repicea.math.integral.GaussHermiteQuadrature");
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.AbstractGaussQuadrature", 
				"repicea.math.integral.AbstractGaussQuadrature");
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.AbstractGaussQuadrature$NumberOfPoints", 
				"repicea.math.integral.AbstractGaussQuadrature$NumberOfPoints");

	}
	
	static MetaModel MetaModelInstance;
	
		
	@BeforeClass
	public static void deserializingMetaModel() throws IOException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_FMU02664_RE2_NoChange_AliveVolume_AllSpecies.zml";
		MetaModelInstance = MetaModel.Load(metaModelFilename);
		MetaModelInstance.getMetropolisHastingsParameters().nbBurnIn = 1000;
		MetaModelInstance.getMetropolisHastingsParameters().nbAcceptedRealizations = 11000;
		MetaModelInstance.getMetropolisHastingsParameters().nbInitialGrid = 0;
		MetaModelInstance.getMetropolisHastingsParameters().oneEach = 25;
	}
	
	@AfterClass
	public static void removeSingleton() {
		MetaModelInstance = null;
	}

	@Test
	public void test01MetaModelDeserialization() throws IOException, MetaModelException {
		Assert.assertTrue("Model is deserialized", MetaModelInstance != null);
		Assert.assertTrue("Has converged", MetaModelInstance.hasConverged());
		String filename = ObjectUtility.getPackagePath(getClass()) + "finalDataSet.csv";
		MetaModelInstance.exportFinalDataSet(filename);
		int actualNbOfRecords = MetaModelInstance.getFinalDataSet().getNumberOfObservations();
		Assert.assertEquals("Testing final dataset size", 60, actualNbOfRecords);
	}
	
	@Test
	public void test02OutputTypes() throws Exception {
		List<String> outputTypes = MetaModelInstance.getPossibleOutputTypes();
		Assert.assertEquals("Testing list size", 3, outputTypes.size());
		Assert.assertEquals("Testing first value", "AliveVolume_AllSpecies", outputTypes.get(0));
		Assert.assertEquals("Testing second value", "AliveVolume_BroadleavedSpecies", outputTypes.get(1));
		Assert.assertEquals("Testing third value", "AliveVolume_ConiferousSpecies", outputTypes.get(2));
	}

	@Test
	public void test03MetaModelPrediction() throws Exception {
		double pred = MetaModelInstance.getPrediction(90, 0);
		Assert.assertEquals("Testing prediction at 90 yrs of age", 104.26481827545614, pred, 1E-8);
	}

	@Test
	public void test04MetaModelMCPredictionWithNoVaribility() throws Exception {
		DataSet pred = MetaModelInstance.getMonteCarloPredictions(new int[] {0,10,20,30}, 0, 0, 0);
		Assert.assertEquals("Testing prediction at t0", 0d, (double) pred.getValueAt(0, "Pred"), 1E-8);
		Assert.assertEquals("Testing prediction at t10", 4.1785060702519825, (double) pred.getValueAt(1, "Pred"), 1E-8);
		Assert.assertEquals("Testing prediction at t20", 14.499085383998823, (double) pred.getValueAt(2, "Pred"), 1E-8);
		Assert.assertEquals("Testing prediction at t30", 28.161390838930085 , (double) pred.getValueAt(3, "Pred"), 1E-8);
	}

	@Test
	public void test05AddScriptResult() {
		MetaModel mm = new MetaModel("RE2", "QC", "TSP4");
		DataSet ds = ScriptResult.createEmptyDataSet()		;
		ds.addObservation(new Object[] {1970, 0, "patate", 25.2, 25.2, "allo"});
		ds.addObservation(new Object[] {1980, 0, "carotte", 32d, 35.2, "allo2"});
		ds.indexFieldType();
		ScriptResult sr = new ScriptResult(500, 20, RepresentativeConcentrationPathway.RCP2_6, "Artemis", ds);
		mm.addScriptResult(30, sr);
		
		ds = ScriptResult.createEmptyDataSet()		;
		ds.addObservation(new Object[] {2021, 0, "patate", 15.2, 25.4, "allo"});
		ds.addObservation(new Object[] {2031, 0, "carotte", 16d, 15.2, "allo2"});
		ds.indexFieldType();
		sr = new ScriptResult(500, 20, RepresentativeConcentrationPathway.RCP2_6, "Artemis", ds);
		mm.addScriptResult(50, sr);
		Assert.assertEquals("Testing scriptResults map size", 2, mm.scriptResults.size());
	}
	
	@Test
	public void test06StratumAgeInDataset() {
		DataSet ds = MetaModelInstance.convertScriptResultsIntoDataSet();
		Assert.assertTrue("Testing if stratum age is part of the dataset", ds.getFieldNames().contains(MetaModel.STRATUM_AGE_STR));
	}
	
	
	/**
	 * Test whether the JSON string can be properly deserialized.<p>
	 * The test will throw an exception if the JSON string cannot be deserialized.
	 * @throws MetaModelException 
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void test07JSONParameterisation() throws MetaModelException {
		LinkedHashMap<String, Object>[] parms = new LinkedHashMap[4];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "710", "Uniform", new String[] {"0", "2000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.008", "Uniform", new String[] {"0.00001", "0.05"}});
		parms[2] = MetaModel.convertParameters(new Object[] {"b3", "1.4", "Uniform", new String[] {"0.8", "6"}});
		parms[3] = MetaModel.convertParameters(new Object[] {"rho", "0.99", "Uniform", new String[] {"0.8", "0.995"}});

		Map<String, Object> args = new HashMap<String, Object>();
		args.put(JsonWriter.TYPE, false);
		String jsonStr = JsonWriter.objectToJson(parms, args);
		LinkedHashMap<String, Object> startingValuesMap = new LinkedHashMap<String, Object>();
		startingValuesMap.put(ModelImplEnum.ChapmanRichardsDerivative.name(), jsonStr);
		System.out.println(MetaModelInstance.getPossibleOutputTypes());
				
		MetaModelInstance.fitModel("AliveVolume_AllSpecies", startingValuesMap);
		Assert.assertEquals("Testing final sample size", 400, MetaModelInstance.model.mh.convertMetropolisHastingsSampleToDataSet().getNumberOfObservations());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void test08ExponentialModelForStemDensity() throws IOException {
		String filename = ObjectUtility.getPackagePath(getClass()) + "QC_6OUEST_STR_ME1_6OUEST_NoChange_AliveVolume_AllSpecies.zml";
		MetaModel m = MetaModel.Load(filename);
		m.getMetropolisHastingsParameters().nbBurnIn = 1000;
		m.getMetropolisHastingsParameters().nbAcceptedRealizations = 11000;
		m.getMetropolisHastingsParameters().nbInitialGrid = 0;
		m.getMetropolisHastingsParameters().oneEach = 25;

		LinkedHashMap<String, Object>[] parms = new LinkedHashMap[3];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "2000", "Uniform", new String[] {"0", "8000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.005", "Uniform", new String[] {"0.00001", "0.05"}});
		parms[2] = MetaModel.convertParameters(new Object[] {"rho", "0.99", "Uniform", new String[] {"0.8", "0.995"}});

		Map<String, Object> args = new HashMap<String, Object>();
		args.put(JsonWriter.TYPE, false);
		String jsonStr = JsonWriter.objectToJson(parms, args);
		LinkedHashMap<String, Object> startingValuesMap = new LinkedHashMap<String, Object>();
		startingValuesMap.put(ModelImplEnum.Exponential.name(), jsonStr);
		m.fitModel("AliveStemDensity_AllSpecies", startingValuesMap);
		System.out.println(m.getSummary());
		Assert.assertEquals("Testing b1", 1225d, m.getFinalParameterEstimates().getValueAt(0, 0), 50d);
		Assert.assertEquals("Testing b2", 0.0029, m.getFinalParameterEstimates().getValueAt(1, 0), 0.001);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void test09ExponentialModelWithRandomEffectForStemDensity() throws IOException {
		String filename = ObjectUtility.getPackagePath(getClass()) + "QC_6OUEST_STR_ME1_6OUEST_NoChange_AliveVolume_AllSpecies.zml";
		MetaModel m = MetaModel.Load(filename);
		m.getMetropolisHastingsParameters().nbBurnIn = 1000;
		m.getMetropolisHastingsParameters().nbAcceptedRealizations = 11000;
		m.getMetropolisHastingsParameters().nbInitialGrid = 0;
		m.getMetropolisHastingsParameters().oneEach = 25;

		LinkedHashMap<String, Object>[] parms = new LinkedHashMap[4];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "2000", "Uniform", new String[] {"0", "8000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.005", "Uniform", new String[] {"0.00001", "0.05"}});
		parms[2] = MetaModel.convertParameters(new Object[] {AbstractModelImplementation.CORRELATION_PARM, "0.99", "Uniform", new String[] {"0.8", "0.995"}});
		parms[3] = MetaModel.convertParameters(new Object[] {AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD, "1000", "Uniform", new String[] {"0", "3000"}});

		Map<String, Object> args = new HashMap<String, Object>();
		args.put(JsonWriter.TYPE, false);
		String jsonStr = JsonWriter.objectToJson(parms, args);
		LinkedHashMap<String, Object> startingValuesMap = new LinkedHashMap<String, Object>();
		startingValuesMap.put(ModelImplEnum.ExponentialWithRandomEffect.name(), jsonStr);
		m.fitModel("AliveStemDensity_AllSpecies", startingValuesMap);
		System.out.println(m.getSummary());
		Assert.assertEquals("Testing b2", 0.0029, m.getFinalParameterEstimates().getValueAt(1, 0), 0.001);
		Assert.assertEquals("Testing rho", 0.83, m.getFinalParameterEstimates().getValueAt(2, 0), 0.02);
	}

	
	@SuppressWarnings("unchecked")
	@Test
	public void test10ExponentialWithAndWithoutRandomEffectsForStemDensity() throws IOException {
		String filename = ObjectUtility.getPackagePath(getClass()) + "QC_6OUEST_STR_ME1_6OUEST_NoChange_AliveVolume_AllSpecies.zml";
		MetaModel m = MetaModel.Load(filename);
		m.getMetropolisHastingsParameters().nbBurnIn = 1000;
		m.getMetropolisHastingsParameters().nbAcceptedRealizations = 11000;
		m.getMetropolisHastingsParameters().nbInitialGrid = 0;
		m.getMetropolisHastingsParameters().oneEach = 25;

		LinkedHashMap<String, Object>[] parms = new LinkedHashMap[3];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "2000", "Uniform", new String[] {"0", "8000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.005", "Uniform", new String[] {"0.00001", "0.05"}});
		parms[2] = MetaModel.convertParameters(new Object[] {"rho", "0.99", "Uniform", new String[] {"0.8", "0.995"}});

		Map<String, Object> args = new HashMap<String, Object>();
		args.put(JsonWriter.TYPE, false);
		String jsonStr = JsonWriter.objectToJson(parms, args);
		LinkedHashMap<String, Object> startingValuesMap = new LinkedHashMap<String, Object>();
		startingValuesMap.put(ModelImplEnum.Exponential.name(), jsonStr);
		
		parms = new LinkedHashMap[4];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "2000", "Uniform", new String[] {"0", "8000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.005", "Uniform", new String[] {"0.00001", "0.05"}});
		parms[2] = MetaModel.convertParameters(new Object[] {AbstractModelImplementation.CORRELATION_PARM, "0.99", "Uniform", new String[] {"0.8", "0.995"}});
		parms[3] = MetaModel.convertParameters(new Object[] {AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD, "1000", "Uniform", new String[] {"0", "3000"}});

		jsonStr = JsonWriter.objectToJson(parms, args);
		startingValuesMap.put(ModelImplEnum.ExponentialWithRandomEffect.name(), jsonStr);
		
		m.fitModel("AliveStemDensity_AllSpecies", startingValuesMap);
		System.out.println(m.getModelComparison());
		DataSet outputComparison = m.getModelComparison();
		int implementationFieldIndex = outputComparison.getFieldNames().indexOf("ModelImplementation");
		List implementationValues = outputComparison.getFieldValues(implementationFieldIndex);
		Assert.assertEquals("Testing list size", 2, implementationValues.size());
		Assert.assertTrue("List contains Exponential", implementationValues.contains(ModelImplEnum.Exponential.name()));
		Assert.assertTrue("List contains ExponentialWithRandomEffect", implementationValues.contains(ModelImplEnum.ExponentialWithRandomEffect.name()));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void test11ModifiedChapmanRichardsDerivativeForStemDensity() throws IOException {
		String filename = ObjectUtility.getPackagePath(getClass()) + "QC_6OUEST_STR_ME1_6OUEST_NoChange_AliveVolume_AllSpecies.zml";
		MetaModel m = MetaModel.Load(filename);
		m.getMetropolisHastingsParameters().nbBurnIn = 1000;
		m.getMetropolisHastingsParameters().nbAcceptedRealizations = 11000;
		m.getMetropolisHastingsParameters().nbInitialGrid = 0;
		m.getMetropolisHastingsParameters().oneEach = 25;

		Map<String, Object> args = new HashMap<String, Object>();
		args.put(JsonWriter.TYPE, false);
		LinkedHashMap<String, Object> startingValuesMap = new LinkedHashMap<String, Object>();
		
		LinkedHashMap<String, Object>[] parms = new LinkedHashMap[4];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "5000", "Uniform", new String[] {"0", "10000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.005", "Uniform", new String[] {"0.0001", "0.01"}});
		parms[2] = MetaModel.convertParameters(new Object[] {"b3", "0.2", "Uniform", new String[] {"0.001", "0.5"}});
		parms[3] = MetaModel.convertParameters(new Object[] {AbstractModelImplementation.CORRELATION_PARM, "0.92", "Uniform", new String[] {"0.8", "0.995"}});

		String jsonStr = JsonWriter.objectToJson(parms, args);
		startingValuesMap.put(ModelImplEnum.ModifiedChapmanRichardsDerivative.name(), jsonStr);
		
		m.fitModel("AliveStemDensity_AllSpecies", startingValuesMap);
		System.out.println(m.getSummary());
		Assert.assertEquals("Testing b1", 1225, m.getFinalParameterEstimates().getValueAt(0,0), 100);
		Assert.assertEquals("Testing b2", 0.0029, m.getFinalParameterEstimates().getValueAt(1,0), 0.001);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void test12ModifiedChapmanRichardsDerivativeWithRandomEffectForStemDensity() throws IOException {
		String filename = ObjectUtility.getPackagePath(getClass()) + "QC_6OUEST_STR_ME1_6OUEST_NoChange_AliveVolume_AllSpecies.zml";
		MetaModel m = MetaModel.Load(filename);
		m.getMetropolisHastingsParameters().nbBurnIn = 1000;
		m.getMetropolisHastingsParameters().nbAcceptedRealizations = 11000;
		m.getMetropolisHastingsParameters().nbInitialGrid = 10000;
		m.getMetropolisHastingsParameters().oneEach = 25;

		Map<String, Object> args = new HashMap<String, Object>();
		args.put(JsonWriter.TYPE, false);
		LinkedHashMap<String, Object> startingValuesMap = new LinkedHashMap<String, Object>();
		
		LinkedHashMap<String, Object>[] parms = new LinkedHashMap[5];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "5000", "Uniform", new String[] {"0", "10000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.005", "Uniform", new String[] {"0.0001", "0.01"}});
		parms[2] = MetaModel.convertParameters(new Object[] {"b3", "0.2", "Uniform", new String[] {"0.001", "0.5"}});
		parms[3] = MetaModel.convertParameters(new Object[] {AbstractModelImplementation.CORRELATION_PARM, "0.92", "Uniform", new String[] {"0.8", "0.995"}});
		parms[4] = MetaModel.convertParameters(new Object[] {AbstractMixedModelFullImplementation.RANDOM_EFFECT_STD, "500", "Uniform", new String[] {"0", "2000"}});

		String jsonStr = JsonWriter.objectToJson(parms, args);
		startingValuesMap.put(ModelImplEnum.ModifiedChapmanRichardsDerivativeWithRandomEffect.name(), jsonStr);
		
		m.fitModel("AliveStemDensity_AllSpecies", startingValuesMap);
		System.out.println(m.getSummary());
		Assert.assertEquals("Testing b1", 1220, m.getFinalParameterEstimates().getValueAt(0,0), 120);
		Assert.assertEquals("Testing b2", 0.0029, m.getFinalParameterEstimates().getValueAt(1,0), 0.001);
	}
	
	@Test
	public void test13MetaModelRE38With10YrOldStratum() throws IOException {
//		REpiceaTranslator.setCurrentLanguage(Language.English);
//        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %5$s%6$s%n");
//		REpiceaLogManager.getLogger(MetaModelManager.LoggerName).setLevel(Level.INFO);
//		ConsoleHandler sh = new ConsoleHandler();
//		sh.setLevel(Level.INFO);
//		sh.setFormatter(new SimpleFormatter());
//		REpiceaLogManager.getLogger(MetaModelManager.LoggerName).addHandler(sh);

		String path = ObjectUtility.getPackagePath(MetaModelTest.class);
		String metaModelFilename = path + "QC_3EST_RS38" + "_NoChange.zml";
		MetaModel metaModel = MetaModel.Load(metaModelFilename);
		LinkedHashMap<String, Object> implementations = new LinkedHashMap<String, Object>();
		implementations.put(ModelImplEnum.ChapmanRichards.name(), null);
		implementations.put(ModelImplEnum.ChapmanRichardsWithRandomEffect.name(), null);
		implementations.put(ModelImplEnum.ChapmanRichardsDerivative.name(), null);
		implementations.put(ModelImplEnum.ChapmanRichardsDerivativeWithRandomEffect.name(), null);
		metaModel.fitModel("AliveVolume_AllSpecies", implementations);
		System.out.println(metaModel.getModelComparison());
		System.out.println(metaModel.getSummary());
		boolean properImplementation = metaModel.model instanceof ChapmanRichardsDerivativeModelImplementation;
		Assert.assertTrue("Proper model implementation was selected", properImplementation);
		Matrix parms = metaModel.getFinalParameterEstimates();
		Assert.assertEquals("Testing parameter b1", 1185, parms.getValueAt(0, 0), 10);
		Assert.assertEquals("Testing parameter resLag", 8.87, parms.getValueAt(4, 0), .2);
	}

	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws IOException, MetaModelException {
//		AbstractModelImplementation.EstimateResidualVariance = true;
		REpiceaTranslator.setCurrentLanguage(Language.English);
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %5$s%6$s%n");
		REpiceaLogManager.getLogger(MetaModelManager.LoggerName).setLevel(Level.FINE);
		ConsoleHandler sh = new ConsoleHandler();
		String outputPath = ObjectUtility.getPackagePath(MetaModelTest.class);
		sh.setLevel(Level.FINE);
		sh.setFormatter(new SimpleFormatter());
		REpiceaLogManager.getLogger(MetaModelManager.LoggerName).addHandler(sh);
		
		String path = ObjectUtility.getPackagePath(MetaModelTest.class);
		List<String> vegPotList = new ArrayList<String>();
//		vegPotList.add("MS2");
//		vegPotList.add("RE1");
//		vegPotList.add("RE2");
//		vegPotList.add("RE3");
		vegPotList.add("RS2");
//		vegPotList.add("RS3");
		
		List<String> outputTypes = new ArrayList<String>();
		outputTypes.add("AliveVolume_AllSpecies");

		LinkedHashMap<String, Object>[] parms = new LinkedHashMap[5];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "834", "Uniform", new String[] {"0", "2000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.0078", "Uniform", new String[] {"0.00001", "0.05"}});
		parms[2] = MetaModel.convertParameters(new Object[] {"b3", "2.0", "Uniform", new String[] {"0.8", "6"}});
		parms[3] = MetaModel.convertParameters(new Object[] {"rho", "0.97", "Uniform", new String[] {"0.8", "0.995"}});
		parms[4] = MetaModel.convertParameters(new Object[] {"sigma_u", "50", "Uniform", new String[] {"0", "200"}});
		LinkedHashMap<String, Object> implementations = new LinkedHashMap<String, Object>();
		implementations.put(ModelImplEnum.ChapmanRichardsDerivativeWithRandomEffect.name(), parms);
		parms = new LinkedHashMap[4];
		parms[0] = MetaModel.convertParameters(new Object[] {"b1", "710", "Uniform", new String[] {"0", "2000"}});
		parms[1] = MetaModel.convertParameters(new Object[] {"b2", "0.008", "Uniform", new String[] {"0.00001", "0.05"}});
		parms[2] = MetaModel.convertParameters(new Object[] {"b3", "1.4", "Uniform", new String[] {"0.8", "6"}});
		parms[3] = MetaModel.convertParameters(new Object[] {"rho", "0.99", "Uniform", new String[] {"0.8", "0.995"}});
		implementations.put(ModelImplEnum.ChapmanRichardsDerivative.name(), parms);
		
		for (String vegPot : vegPotList) {
			String metaModelFilename = path + "QC_FMU02664_" + vegPot + "_NoChange_root.zml";
			for (String outputType : outputTypes) {
				MetaModel m = MetaModel.Load(metaModelFilename);
				m.mhSimParms.nbInitialGrid = 0;
				m.mhSimParms.nbBurnIn = 50000;
				m.mhSimParms.nbAcceptedRealizations = 1000000 + m.mhSimParms.nbBurnIn;
				m.fitModel(outputType, implementations);
//				UNCOMMENT THIS LINE TO UPDATE THE META MODELS
//				m.save(path + "QC_FMU02664_" + vegPot + "_NoChange_AliveVolume_AllSpecies.zml");
				m.exportMetropolisHastingsSample(outputPath + File.separator + vegPot + "_" + outputType + "MHSample.csv");
				m.exportFinalDataSet(outputPath + File.separator + vegPot + "_" + outputType + ".csv");
				System.out.println(m.getModelComparison().toString());
				System.out.println(m.getSummary());
//				m.getModelComparison().save(outputPath + File.separator + vegPot + "_" + outputType + "ModelComparison.csv");
			}
		}
//		String jsonStr = "[{\"Parameter\":\"b1\",\"StartingValue\":5500,\"Distribution\":\"Uniform\",\"DistParms\":[\"0\",\"300\"]},{\"Parameter\":\"b2\",\"StartingValue\":0.007,\"Distribution\":\"Uniform\",\"DistParms\":[\"0.0001\",\"0.02\"]},{\"Parameter\":\"b3\",\"StartingValue\":2,\"Distribution\":\"Uniform\",\"DistParms\":[\"1\",\"6\"]},{\"Parameter\":\"rho\",\"StartingValue\":0.98,\"Distribution\":\"Uniform\",\"DistParms\":[\"0.8\",\"0.995\"]},{\"Parameter\":\"sigma_u\",\"StartingValue\":10,\"Distribution\":\"Uniform\",\"DistParms\":[\"0\",\"150\"]}]";
//		MetaModel m = MetaModel.Load(outputPath + "FittedMetamodel_Coniferous_AllAlive_FMU02664.zml");
//		System.out.println(m.getSummary());
//		LinkedHashMap<String, Object> models = new LinkedHashMap<String, Object>();
//		models.put(ModelImplEnum.ChapmanRichardsDerivativeWithRandomEffect.name(), jsonStr);
//		m.fitModel(m.getSelectedOutputType(), models);
//		System.out.println(m.getSummary());
//		int u = 0;
	}
	
}
