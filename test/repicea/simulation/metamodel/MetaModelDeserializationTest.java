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

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import repicea.util.ObjectUtility;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MetaModelDeserializationTest {

	// IMPORTANT this test must come first to ensure both versions of the meta-model exist.
	@Test
	public void conversionTest() throws IOException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_FMU02664_RE2_NoChange_AliveVolume_AllSpecies.zml";
		File originalFile = new File(metaModelFilename);
		if (!originalFile.exists()) {
			Assert.fail("The file does not exist: " + metaModelFilename);
		}
		
		MetaModel.convertToLightVersion(metaModelFilename);
		
		String newFilename = MetaModel.getLightVersionFilename(metaModelFilename);
		File newFile = new File(newFilename);
		if (!newFile.exists()) {
			Assert.fail("The converted file has not been saved!");
		}

		long originalFileSize = Files.size(Paths.get(originalFile.toURI()));
		Assert.assertTrue("Testing file size for original version", originalFileSize > 3E6);
		long newFileSize = Files.size(Paths.get(newFile.toURI()));
		Assert.assertTrue("Testing file size for original version", newFileSize < 45000);
	}

	@Test
	public void deserializationTest() throws IOException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_FMU02664_RE2_NoChange_AliveVolume_AllSpecies.zml";
		String newFilename = MetaModel.getLightVersionFilename(metaModelFilename);

		long startingTime = System.currentTimeMillis();
		MetaModel originalVersion = MetaModel.Load(metaModelFilename);
		long loadingTimeOriginal = System.currentTimeMillis() - startingTime;
		
		startingTime = System.currentTimeMillis();
		MetaModel lightVersion = MetaModel.Load(newFilename);
		long loadingTimeLight = System.currentTimeMillis() - startingTime;

		double loadingRatio = (double) loadingTimeOriginal / loadingTimeLight;
		System.out.println("Loading ratio = " + loadingRatio);
		Assert.assertTrue("Testing load time for original version", loadingRatio > 50);
	}

}
