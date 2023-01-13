package repicea;

import org.junit.Assert;
import org.junit.Test;

import repicea.simulation.metamodel.MetaModelManager;

public class ProductionEnvironmentTest {
	@Test 
	
	/**
	 * This test will fail when used with a local class folder, but will succeed when running on a JAR file.
	 * This test is excluded from the normal local tests and executed only with the integration tests from the JAR file
	 */
	public void makeSurePackageIsRunningFromJAR() {
		String resourceURL = MetaModelManager.class.getResource("MetaModelManager.class").toString();
		Assert.assertTrue(resourceURL.startsWith("jar:"));
	}
}
