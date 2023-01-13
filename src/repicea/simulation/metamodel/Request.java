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

import repicea.simulation.covariateproviders.MethodProviderEnum.VariableForEstimation;
import repicea.simulation.covariateproviders.treelevel.TreeStatusProvider.StatusClass;

public enum Request {
	/**
	 * Periodic annual growth (m3/ha)
	 */
	PeriodicAnnualGrowth(false, null, null),
	/**
	 * Periodic annual mortality (m3/ha)
	 */
	PeriodicAnnualMortality(false, null, null),
	/**
	 * Periodic annual recruitment (m3/ha)
	 */
	PeriodicAnnualRecruitment(false, null, null),
	/**
	 * Periodic annual harvesting (m3/ha)
	 */
	PeriodicAnnualHarvesting(false, null, null),
	/**
	 * Proportion of harvested area per treatment over the last growth step
	 */
	ProportionHarvestedAreaPerTreatment(false, null, null),
	/**
	 * Volume of alive trees (m3/ha)
	 */
	AliveVolume(true, StatusClass.alive, VariableForEstimation.V),
	/**
	 * Volume of harvested trees (m3/ha)
	 */
	CutVolume(true, StatusClass.cut, VariableForEstimation.V),
	/**
	 * Volume of dead trees (m3/ha)
	 */
	DeadVolume(true, StatusClass.dead, VariableForEstimation.V),
	/**
	 * Volume of windthown trees (m3/ha)
	 */
	WindfallVolume(true, StatusClass.windfall, VariableForEstimation.V),
	
	/**
	 * Biomass (Mg)
	 */
	AliveAboveGroundBiomass(true, StatusClass.alive, VariableForEstimation.B);
	
	final StatusClass statusClass;
	final VariableForEstimation variableForEstimation;
	
	final boolean isMeantForInitialStand;
	
	Request(boolean isMeantForInitialStand, StatusClass statusClass, VariableForEstimation variableForEstimation) {
		this.isMeantForInitialStand = isMeantForInitialStand;
		this.statusClass = statusClass;
		this.variableForEstimation = variableForEstimation;
	}
	
	public boolean getIsMeantForInitialStand() {
		return isMeantForInitialStand;
	}
	
	public StatusClass getStatusClass() {
		return statusClass;
	}
	
	public VariableForEstimation getVariableForEstimation() {
		return variableForEstimation;
	}
}
