/*
 * This file is part of the repicea-metamodel library.
 *
 * Copyright (C) 2025 His Majesty the King in Right of Canada
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import repicea.math.Matrix;
import repicea.simulation.metamodel.MetaModel.PredictionVarianceOutputType;
import repicea.stats.data.DataSet;
import repicea.stats.estimates.MonteCarloEstimate;
import repicea.util.ObjectUtility;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConcurrenceTest {

	private static class InternalDeterministicWorker extends Thread {
		final MetaModel mm;
		final MonteCarloEstimate estimate;
		final PredictionVarianceOutputType varianceType;
		
		InternalDeterministicWorker(MetaModel mm, int k, PredictionVarianceOutputType varianceType) {
			super("Tread " + k);
			this.mm = mm;
			this.estimate = new MonteCarloEstimate();
			this.varianceType = varianceType;
		}
		
		@Override
		public void run() {
			try {
				for (int i = 0; i < 10000; i++) {
					DataSet ds = mm.getPredictions(new int[] {0,10,20,30,40,50,60}, 0, varianceType);
					List<Double> os = (List) ds.getFieldValues(ds.getIndexOfThisField("Pred"));
					Matrix realization = new Matrix(os);
					estimate.addRealization(realization);
				}
			} catch (MetaModelException e) {
				throw new RuntimeException(e);
			}
		}
		
	}

	
	private static class InternalStochasticWorker extends Thread {
		final MetaModel mm;
		
		InternalStochasticWorker(MetaModel mm, int k) {
			super("Tread " + k);
			this.mm = mm;
		}
		
		@Override
		public void run() {
			try {
				for (int i = 0; i < 10000; i++) {
					DataSet ds = mm.getMonteCarloPredictions(new int[] {0,10,20,30,40,50,60}, 0, 10, 10);
//					List<Double> os = (List) ds.getFieldValues(ds.getIndexOfThisField("Pred"));
//					Matrix realization = new Matrix(os);
//					estimate.addRealization(realization);
				}
			} catch (MetaModelException e) {
				throw new RuntimeException(e);
			}
		}
		
	}

	
	@Test
	public void test01PredictVarNONE() throws IOException, InterruptedException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_USGCTile184_Tile184_NoChange_AliveAboveGroundBiomass_AllSpecies_light.zml";
		MetaModel mm = MetaModel.Load(metaModelFilename);
		List<InternalDeterministicWorker> workers = new ArrayList<InternalDeterministicWorker>();
		int nbWorkers = 4;
		for (int k = 0; k < nbWorkers; k++) {
			workers.add(new InternalDeterministicWorker(mm, k, PredictionVarianceOutputType.NONE));
		}
		
		for (InternalDeterministicWorker w : workers) {
			w.start();
		}

		for (InternalDeterministicWorker w : workers) {
			w.join();
		}

		Matrix mean = null;
		for (InternalDeterministicWorker w : workers) {
			if (mean == null) {
				mean = w.estimate.getMean();
			} else {
				Matrix wMean = w.estimate.getMean();
				Assert.assertTrue("Testing if the mean is consistent", !mean.subtract(wMean).getAbsoluteValue().anyElementLargerThan(1E-8));			
			}
			Matrix wVariance = w.estimate.getVariance();
			Assert.assertTrue("Testing if variance is consistent", !wVariance.getAbsoluteValue().anyElementLargerThan(1E-8));
		}
	}
	
	@Test
	public void test02PredictVarPARAMEST() throws IOException, InterruptedException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_USGCTile184_Tile184_NoChange_AliveAboveGroundBiomass_AllSpecies_light.zml";
		MetaModel mm = MetaModel.Load(metaModelFilename);
		List<InternalDeterministicWorker> workers = new ArrayList<InternalDeterministicWorker>();
		int nbWorkers = 4;
		for (int k = 0; k < nbWorkers; k++) {
			workers.add(new InternalDeterministicWorker(mm, k, PredictionVarianceOutputType.PARAMEST));
		}
		
		for (InternalDeterministicWorker w : workers) {
			w.start();
		}

		for (InternalDeterministicWorker w : workers) {
			w.join();
		}

		Matrix mean = null;
		for (InternalDeterministicWorker w : workers) {
			if (mean == null) {
				mean = w.estimate.getMean();
			} else {
				Matrix wMean = w.estimate.getMean();
				Assert.assertTrue("Testing if the mean is consistent", !mean.subtract(wMean).getAbsoluteValue().anyElementLargerThan(1E-8));			
			}
			Matrix wVariance = w.estimate.getVariance();
			Assert.assertTrue("Testing if variance is consistent", !wVariance.getAbsoluteValue().anyElementLargerThan(1E-8));
		}

		
	}

	@Test
	public void test03PredictVarPARAMESTRE() throws IOException, InterruptedException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_USGCTile184_Tile184_NoChange_AliveAboveGroundBiomass_AllSpecies_light.zml";
		MetaModel mm = MetaModel.Load(metaModelFilename);
		List<InternalDeterministicWorker> workers = new ArrayList<InternalDeterministicWorker>();
		int nbWorkers = 4;
		for (int k = 0; k < nbWorkers; k++) {
			workers.add(new InternalDeterministicWorker(mm, k, PredictionVarianceOutputType.PARAMESTRE));
		}
		
		for (InternalDeterministicWorker w : workers) {
			w.start();
		}

		for (InternalDeterministicWorker w : workers) {
			w.join();
		}

		Matrix mean = null;
		for (InternalDeterministicWorker w : workers) {
			if (mean == null) {
				mean = w.estimate.getMean();
			} else {
				Matrix wMean = w.estimate.getMean();
				Assert.assertTrue("Testing if the mean is consistent", !mean.subtract(wMean).getAbsoluteValue().anyElementLargerThan(1E-8));			
			}
			Matrix wVariance = w.estimate.getVariance();
			Assert.assertTrue("Testing if variance is consistent", !wVariance.getAbsoluteValue().anyElementLargerThan(1E-8));
		}
	}

	@Test
	public void test04PredictMonteCarlo() throws IOException, InterruptedException {
		String metaModelFilename = ObjectUtility.getPackagePath(MetaModelTest.class) + "QC_USGCTile184_Tile184_NoChange_AliveAboveGroundBiomass_AllSpecies_light.zml";
		MetaModel mm = MetaModel.Load(metaModelFilename);
		List<InternalStochasticWorker> workers = new ArrayList<InternalStochasticWorker>();
		int nbWorkers = 2;
		for (int k = 0; k < nbWorkers; k++) {
			workers.add(new InternalStochasticWorker(mm, k));
		}
		
		for (InternalStochasticWorker w : workers) {
			w.start();
		}

		for (InternalStochasticWorker w : workers) {
			w.join();
		}
	}

	
}
