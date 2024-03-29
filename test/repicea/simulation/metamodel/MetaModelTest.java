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
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import repicea.serial.SerializerChangeMonitor;
import repicea.stats.data.DataSet;
import repicea.util.ObjectUtility;
import repicea.util.REpiceaLogManager;
import repicea.util.REpiceaTranslator;
import repicea.util.REpiceaTranslator.Language;

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
	}
	
	@AfterClass
	public static void removeSingleton() {
		MetaModelInstance = null;
	}

	@Test
	public void testingMetaModelDeserialization() throws IOException, MetaModelException {
		Assert.assertTrue("Model is deserialized", MetaModelInstance != null);
		Assert.assertTrue("Has converged", MetaModelInstance.hasConverged());
		String filename = ObjectUtility.getPackagePath(getClass()) + "finalDataSet.csv";
		MetaModelInstance.exportFinalDataSet(filename);
		int actualNbOfRecords = MetaModelInstance.getFinalDataSet().getNumberOfObservations();
		Assert.assertEquals("Testing final dataset size", 60, actualNbOfRecords);
	}
	
	@Test
	public void testingOutputTypes() throws Exception {
		List<String> outputTypes = MetaModelInstance.getPossibleOutputTypes();
		Assert.assertEquals("Testing list size", 3, outputTypes.size());
		Assert.assertEquals("Testing first value", "AliveVolume_AllSpecies", outputTypes.get(0));
		Assert.assertEquals("Testing second value", "AliveVolume_BroadleavedSpecies", outputTypes.get(1));
		Assert.assertEquals("Testing third value", "AliveVolume_ConiferousSpecies", outputTypes.get(2));
	}

	@Test
	public void testingMetaModelPrediction() throws Exception {
		double pred = MetaModelInstance.getPrediction(90, 0);
		Assert.assertEquals("Testing prediction at 90 yrs of age", 104.26481827545614, pred, 1E-8);
	}

	@Test
	public void testingMetaModelMCPredictionWithNoVaribility() throws Exception {
		DataSet pred = MetaModelInstance.getMonteCarloPredictions(new int[] {0,10,20,30}, 0, 0, 0);
		Assert.assertEquals("Testing prediction at t0", 0d, (double) pred.getValueAt(0, "Pred"), 1E-8);
		Assert.assertEquals("Testing prediction at t10", 4.1785060702519825, (double) pred.getValueAt(1, "Pred"), 1E-8);
		Assert.assertEquals("Testing prediction at t20", 14.499085383998823, (double) pred.getValueAt(2, "Pred"), 1E-8);
		Assert.assertEquals("Testing prediction at t30", 28.161390838930085 , (double) pred.getValueAt(3, "Pred"), 1E-8);
	}

	public static void main(String[] args) throws IOException, MetaModelException {
//		AbstractModelImplementation.EstimateResidualVariance = true;
		REpiceaTranslator.setCurrentLanguage(Language.English);
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %5$s%6$s%n");
		REpiceaLogManager.getLogger(MetaModelManager.LoggerName).setLevel(Level.FINE);
		ConsoleHandler sh = new ConsoleHandler();
//		sh.setLevel(Level.FINE);
//		REpiceaLogManager.getLogger(MetaModelManager.LoggerName).addHandler(sh);
//		String outputPath = "C:\\Users\\matforti\\Documents\\7_Developpement\\ModellingProjects\\Quebec\\ProcessedData\\UAF02664\\metaModels";
		String outputPath = ObjectUtility.getPackagePath(MetaModelTest.class);
//		FileHandler sh = new FileHandler(outputPath + "metamodel.log");
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
		
		for (String vegPot : vegPotList) {
			String metaModelFilename = path + "QC_FMU02664_" + vegPot + "_NoChange_root.zml";
			for (String outputType : outputTypes) {
				MetaModel m = MetaModel.Load(metaModelFilename);
				m.mhSimParms.nbInitialGrid = 10000;
				m.mhSimParms.nbBurnIn = 100000;
				m.mhSimParms.nbAcceptedRealizations = 1000000 + m.mhSimParms.nbBurnIn;
				boolean enabledMixedModelImplementation = vegPot.equals("RE1") ? false : true;
				m.fitModel(outputType, enabledMixedModelImplementation);
//				UNCOMMENT THIS LINE TO UPDATE THE META MODELS
//				m.save(path + "QC_FMU02664_" + vegPot + "_NoChange_AliveVolume_AllSpecies.zml");
				m.exportMetropolisHastingsSample(outputPath + File.separator + vegPot + "_" + outputType + "MHSample.csv");
				m.exportFinalDataSet(outputPath + File.separator + vegPot + "_" + outputType + ".csv");
				System.out.println(m.getSummary());
				m.getModelComparison().save(outputPath + File.separator + vegPot + "_" + outputType + "ModelComparison.csv");
			}
		}
	}
}
