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

import java.util.concurrent.LinkedBlockingQueue;

import repicea.simulation.metamodel.MetaModel.ModelImplEnum;

class MetaModelManagerWorker extends Thread implements Runnable {

	static final Object FinishToken = new Object();
	
	final LinkedBlockingQueue<?> queue;
	final String outputType;
	final ModelImplEnum modelImplEnum;
	
	MetaModelManagerWorker(int i, LinkedBlockingQueue<?> queue, String outputType, ModelImplEnum e) {
		super("ExtMetaModelManagerWorker " + i);
		this.queue = queue;
		this.outputType = outputType;
		this.modelImplEnum = e;
		setDaemon(true);
		start();
	}
	
	
	@Override
	public void run() {
		Object o;
		try {
			while(!(o = queue.take()).equals(FinishToken)) {
				MetaModel metaModel = (MetaModel) o;
				metaModel.fitModel(outputType, true);  // true : enabled mixed model implementation
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	
	
}
