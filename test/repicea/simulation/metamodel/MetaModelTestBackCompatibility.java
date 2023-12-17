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

import repicea.serial.SerializerChangeMonitor;
import repicea.util.ObjectUtility;

public class MetaModelTestBackCompatibility {

	static {		
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelManager", "repicea.simulation.metamodel.MetaModelManager");
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel", "repicea.simulation.metamodel.MetaModel");
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.DataBlockWrapper", "repicea.simulation.metamodel.DataBlockWrapper");		
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModelGibbsSample", "repicea.simulation.metamodel.MetaModelGibbsSample");
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$Bound", "repicea.simulation.metamodel.MetaModel$Bound");
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.metamodel.ExtMetaModel$InnerModel", "repicea.simulation.metamodel.MetaModel$InnerModel");		
		SerializerChangeMonitor.registerClassNameChange("capsis.util.extendeddefaulttype.ExtScriptResult", "repicea.simulation.metamodel.ScriptResult");				
		SerializerChangeMonitor.registerClassNameChange("repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation", "repicea.simulation.metamodel.ChapmanRichardsModelWithRandomEffectImplementation");				
		SerializerChangeMonitor.registerClassNameChange("repicea.simulation.metamodel.RichardsChapmanModelWithRandomEffectImplementation$DataBlockWrapper", 
				"repicea.simulation.metamodel.ChapmanRichardsModelWithRandomEffectImplementation$DataBlockWrapper");				
		SerializerChangeMonitor.registerEnumNameChange("repicea.simulation.metamodel.MetaModel$ModelImplEnum", "RichardsChapmanWithRandomEffect", "ChapmanRichardsWithRandomEffect");
		SerializerChangeMonitor.registerClassNameChange("repicea.stats.integral.GaussQuadrature$NumberOfPoints", "repicea.math.integral.AbstractGaussQuadrature$NumberOfPoints");
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
