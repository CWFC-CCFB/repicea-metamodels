/*
 * This file is part of the repicea-util library.
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
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import repicea.serial.xml.XmlSerializerChangeMonitor;
import repicea.util.ObjectUtility;

public class MetaModelTestBackCompatibility {

	static {		
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelManager", "repicea.simulation.metamodel.MetaModelManager");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel", "repicea.simulation.metamodel.MetaModel");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.DataBlockWrapper", "repicea.simulation.metamodel.DataBlockWrapper");		
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelGibbsSample", "repicea.simulation.metamodel.MetaModelGibbsSample");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$Bound", "repicea.simulation.metamodel.MetaModel$Bound");
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$InnerModel", "repicea.simulation.metamodel.MetaModel$InnerModel");		
		XmlSerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.ExtScriptResult", "repicea.simulation.metamodel.ScriptResult");				
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation", "repicea.simulation.metamodel.ChapmanRichardsModelWithRandomEffectImplementation");				
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation$DataBlockWrapper", 
				"repicea.simulation.metamodel.ChapmanRichardsModelWithRandomEffectImplementation$DataBlockWrapper");				
		XmlSerializerChangeMonitor.registerEnumNameChange("repicea.simulation.metamodel.MetaModel$ModelImplEnum", "RichardsChapmanWithRandomEffect", "ChapmanRichardsWithRandomEffect");
		XmlSerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.GaussQuadrature$NumberOfPoints", "repicea.math.integral.AbstractGaussQuadrature$NumberOfPoints");
	}
	


	@Test
	public void testingMetaModelDeserialization2() throws IOException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTestBackCompatibility.class) + File.separator + 
				"QC_FMU02664_MS2_NoChange_AliveVolume_AllSpecies.zml";
		
		MetaModel m = MetaModel.Load(metaModelFilename);

		Assert.assertTrue("Model is deserialized", m != null);
		Assert.assertTrue("Has converged", m.hasConverged());
	}

}
