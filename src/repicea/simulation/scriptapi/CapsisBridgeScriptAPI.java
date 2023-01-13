/*
 * This file is part of the repicea library.
 *
 * Copyright (C) 2009-2021 Mathieu Fortin for Rouge-Epicea
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
package repicea.simulation.scriptapi;

import java.util.List;

import repicea.simulation.ApplicationScaleProvider.ApplicationScale;

/**
 * The script API that a Capsis model script should implement to be compatible with CapsisBridge
 * 
 * Following a discussion on 2021-09-23, it has been decided that this interface should be unique instead of
 * one interface per model, since only initialParameters vary significantly from one model to the other ones.
 *  
 * @see ExtScriptAPI
 * 
 * @author Jean-Francois Lavoie - September 2021
 */
public interface CapsisBridgeScriptAPI extends ExtScriptAPI {
	
	public void setInitialParameters(int initialDateYr, 
			boolean isStochastic, 
			int nbRealizations, 
			ApplicationScale scale,
			String climateChangeOption);	// this is passed as a string so that the implementing script will cast it correctly for use.
		
}
