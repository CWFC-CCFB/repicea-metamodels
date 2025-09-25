/*
 * This file is part of the repicea library.
 *
 * Copyright (C) 2009-2023 Mathieu Fortin for Rouge Epicea.
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.LinkedHashMap;
import java.util.Locale;

import org.junit.Assert;
import org.junit.FixMethodOrder;
//import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import repicea.simulation.metamodel.MetaModel.PredictionVarianceOutputType;
import repicea.util.ObjectUtility;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MetaModelDeserializationTest {

	// IMPORTANT this test must come first to ensure both versions of the meta-model exist.
//	@Ignore
	@Test
	public void test01ConversionTest() throws IOException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_FMU02664_RE2_NoChange_AliveVolume_AllSpecies.zml";
		File originalFile = new File(metaModelFilename);
		if (!originalFile.exists()) {
			Assert.fail("The file does not exist: " + metaModelFilename);
		}

		String newFilename = MetaModel.getLightVersionFilename(metaModelFilename);
		File newFile = new File(newFilename);
		if (newFile.exists()) {
			newFile.delete();
		}
		
		MetaModel.convertToLightVersion(metaModelFilename);
		
		if (!newFile.exists()) {
			Assert.fail("The converted file has not been saved!");
		}

		long originalFileSize = Files.size(Paths.get(originalFile.toURI()));
		System.out.println("Original serialization size " + originalFileSize);
		Assert.assertTrue("Testing file size for original version", originalFileSize > 3E6);
		long newFileSize = Files.size(Paths.get(newFile.toURI()));
		System.out.println("Light serialization size " + newFileSize);
		Assert.assertTrue("Testing file size for light version", newFileSize < 50000);
	}

//	@Ignore
	@Test
	public void test02DeserializationTest() throws IOException, MetaModelException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_FMU02664_RE2_NoChange_AliveVolume_AllSpecies.zml";
		String newFilename = MetaModel.getLightVersionFilename(metaModelFilename);

		long startTime = System.currentTimeMillis();
		MetaModel originalVersion = MetaModel.Load(metaModelFilename);
		long loadingTimeOriginal = System.currentTimeMillis() - startTime;
		
		startTime = System.currentTimeMillis();
		MetaModel lightVersion = MetaModel.Load(newFilename);
		long loadingTimeLight = System.currentTimeMillis() - startTime;

		double loadingRatio = (double) loadingTimeOriginal / loadingTimeLight;
		System.out.println("Loading ratio = " + loadingRatio);
		Assert.assertTrue("Testing load time for original version", loadingRatio > 10);
		LinkedHashMap<String, Object>[] predOriginal = originalVersion.getPredictions(new int[]{10,20,30,40,50}, 0, PredictionVarianceOutputType.NONE).getProtoMapArrayForJSONConversion();
		LinkedHashMap<String, Object>[] predLight = lightVersion.getPredictions(new int[]{10,20,30,40,50}, 0, PredictionVarianceOutputType.NONE).getProtoMapArrayForJSONConversion();
		double expected = (Double) predOriginal[predOriginal.length-1].get("Pred");
		System.out.println("Expected prediction = " + expected);
		double actual = (Double) predLight[predLight.length-1].get("Pred");
		System.out.println("Actual prediction = " + actual);
		
		Assert.assertEquals("Testing predictions", expected, actual, 1E-8);
	}
	
//	@Ignore
	@Test
	public void test03MetaModelManagerTest() throws IOException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_FMU02664_RE2_NoChange_AliveVolume_AllSpecies.zml";
		String newFilename = MetaModel.getLightVersionFilename(metaModelFilename);
		File newFile = new File(newFilename);
		if (newFile.exists()) {
			newFile.delete();
		}
		MetaModelManager manager1 = new MetaModelManager();				
		
		long startTime = System.currentTimeMillis();
		manager1.loadMetaModel("test", metaModelFilename);	// it should serialize a light version
		long loadingTime1 = System.currentTimeMillis() - startTime;

		startTime = System.currentTimeMillis();
		manager1.loadMetaModel("test2", metaModelFilename);	// it should now load the light version directly
		long loadingTime2 = System.currentTimeMillis() - startTime;
		
		double loadingRatio = (double) loadingTime1 / loadingTime2;
		System.out.println("Loading ratio = " + loadingRatio);
		Assert.assertTrue("Testing load time for meta model manager", loadingRatio > 15);
	}

//	@Ignore
	@Test
	public void test04MetaModelManagerTest() throws IOException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_USGCTile184_Tile184_NoChange_AliveAboveGroundBiomass_AllSpecies.zml";
		File originalFile = new File(metaModelFilename);
		if (!originalFile.exists()) {
			Assert.fail("The file does not exist: " + metaModelFilename);
		}

		String newFilename = MetaModel.getLightVersionFilename(metaModelFilename);
		File newFile = new File(newFilename);
		if (newFile.exists()) {
			newFile.delete();
		}
		
		MetaModel.convertToLightVersion(metaModelFilename);
		
		if (!newFile.exists()) {
			Assert.fail("The converted file has not been saved!");
		}

		long originalFileSize = Files.size(Paths.get(originalFile.toURI()));
		System.out.println("Original serialization size " + originalFileSize);
		Assert.assertTrue("Testing file size for original version", originalFileSize > 2E6);
		long newFileSize = Files.size(Paths.get(newFile.toURI()));
		System.out.println("Light serialization size " + newFileSize);
		Assert.assertTrue("Testing file size for light version", newFileSize < 1.1E5);
	}

	@Test
	public void test05MetaModelMetaDataDeserialization() {
		String metaDataFileOld = ObjectUtility.getPackagePath(getClass()) + "QC_2EST_MJ12_NoChange_AliveVolume_Coniferous.json"; 
		MetaModelMetaData metaData1 = MetaModelMetaData.deserializeFromJSONFile(metaDataFileOld);
		Assert.assertEquals("Testing upscaling map size", 4, metaData1.growth.upscaling.size());
		Assert.assertEquals("Testing leading species", "QC", metaData1.growth.geoRegion);
		Assert.assertEquals("Testing leading species", "None", metaData1.fit.leadingSpecies);
		String metaDataFileNew = ObjectUtility.getPackagePath(getClass()) + "QC_2EST_MJ12-EO_NoChange_AliveVolume_Broadleaved.json"; 
		MetaModelMetaData metaData2 = MetaModelMetaData.deserializeFromJSONFile(metaDataFileNew);
		Assert.assertEquals("Testing upscaling map size", 3, metaData2.growth.upscaling.size());
		Assert.assertEquals("Testing leading species", "QC", metaData2.growth.geoRegion);
		Assert.assertEquals("Testing leading species", "EO", metaData2.fit.leadingSpecies);
	}

	@Test
	public void test06MetaModelMetaDataFrenchDateFormatDeserialization() {
		String metaDataFileFrenchDateFormat = ObjectUtility.getPackagePath(getClass()) + "QC_2EST_FE22_NoChange_AliveVolume_AllSpecies.json"; 
		MetaModelMetaData metaData1 = MetaModelMetaData.deserializeFromJSONFile(metaDataFileFrenchDateFormat);
		String dateStr = DateFormat.getDateInstance(DateFormat.FULL, Locale.ENGLISH).format(metaData1.fit.timeStamp);
		Assert.assertEquals("Testing date conversion from French to English", "Friday, April 4, 2025", dateStr);
	}
	
	@Test
	public void test07XmlFormatWithINFString() throws IOException {
		String rootPath = ObjectUtility.getPackagePath(getClass());
		String filename = rootPath + "QC_5EST_MS23_NoChange_AliveStemDensity_AllSpecies.zml";
		MetaModel.Load(filename);
	}


}