package repicea.simulation.scriptapi;

import java.util.LinkedHashMap;
import java.util.List;

import repicea.io.tools.ImportFieldElement.ImportFieldElementIDCard;
import repicea.simulation.covariateproviders.treelevel.SpeciesTypeProvider;
import repicea.simulation.metamodel.Request;
import repicea.simulation.metamodel.ScriptResult;

public interface ExtScriptAPI {
	
	public List<ImportFieldElementIDCard> getFieldDescriptions();
	public void setEvolutionParameters(int finalDateYr);
	public void addRecord(Object[] record);
	public boolean setFieldMatches(int[] indices);
	public ScriptResult runSimulation() throws Exception;
	public void closeProject();		// vient de GScript
	public String getCapsisVersion();
	public List<String> getSpeciesOfType(SpeciesTypeProvider.SpeciesType... type);
	public void registerOutputRequest(Request request, LinkedHashMap<String, List<String>> aggregationPatterns);
}
