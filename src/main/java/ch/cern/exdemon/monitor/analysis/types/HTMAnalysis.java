package ch.cern.exdemon.monitor.analysis.types;

import static org.numenta.nupic.algorithms.Anomaly.KEY_ESTIMATION_SAMPLES;
import static org.numenta.nupic.algorithms.Anomaly.KEY_IS_WEIGHTED;
import static org.numenta.nupic.algorithms.Anomaly.KEY_LEARNING_PERIOD;
import static org.numenta.nupic.algorithms.Anomaly.KEY_USE_MOVING_AVG;
import static org.numenta.nupic.algorithms.Anomaly.KEY_WINDOW_SIZE;
import static org.numenta.nupic.algorithms.Anomaly.VALUE_NONE;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.net.util.Base64;
import org.numenta.nupic.FieldMetaType;
import org.numenta.nupic.Parameters.KEY;
import org.numenta.nupic.algorithms.Anomaly;
import org.numenta.nupic.algorithms.AnomalyLikelihood;
import org.numenta.nupic.algorithms.SpatialPooler;
import org.numenta.nupic.algorithms.TemporalMemory;
import org.numenta.nupic.encoders.DateEncoder;
import org.numenta.nupic.encoders.MultiEncoder;
import org.numenta.nupic.model.Persistable;
import org.numenta.nupic.network.Inference;
import org.numenta.nupic.network.Network;
import org.numenta.nupic.network.Persistence;
import org.numenta.nupic.network.PersistenceAPI;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import ch.cern.exdemon.components.ConfigurationResult;
import ch.cern.exdemon.components.RegisterComponentType;
import ch.cern.exdemon.monitor.analysis.NumericAnalysis;
import ch.cern.exdemon.monitor.analysis.results.AnalysisResult;
import ch.cern.exdemon.monitor.analysis.results.AnalysisResult.Status;
import ch.cern.exdemon.monitor.analysis.types.htm.AnomaliesResults;
import ch.cern.exdemon.monitor.analysis.types.htm.HTMParameters;
import ch.cern.properties.ConfigurationException;
import ch.cern.properties.Properties;
import ch.cern.spark.status.HasStatus;
import ch.cern.spark.status.StatusValue;
import ch.cern.spark.status.storage.ClassNameAlias;
import lombok.ToString;

@RegisterComponentType("htm")
public class HTMAnalysis extends NumericAnalysis implements HasStatus {

	private static final long serialVersionUID = 1015850481683037208L;
	//TODO: check naming convention
	public static final String MIN_VALUE_PARAMS = "htm.min";
	public static final float MIN_VALUE_DEFAULT = 0;
	private float minValue;
	
	public static final String MAX_VALUE_PARAMS = "htm.max";
	public static final float MAX_VALUE_DEFAULT = 1;
	private float maxValue;
	
	public static final String TOD_PARAMS = "htm.season.timeofday";
	public static final boolean TOD_DEFAULT = true;
	private boolean timeOfDay;
	
	public static final String DOW_PARAMS = "htm.season.dateofweek";
	public static final boolean DOW_DEFAULT = false;
	private boolean dateOfWeek;
	
	public static final String WEEKEND_PARAMS = "htm.season.weekend";
	public static final boolean WEEKEND_DEFAULT = false;
	private boolean isWeekend;
	
	public static final String ERROR_THRESHOLD_PARAMS = "error.threshold";
	public static final float ERROR_THRESHOLD_DEFAULT = (float) 0.999;
	public double errorThreshold;
	
	public static final String WARNING_THRESHOLD_PARAMS = "warn.threshold";
	public static final float WARNING_THRESHOLD_DEFAULT = (float) 0.9;
	public double warningThreshold;

	private transient Network network;
	private transient AnomalyLikelihood anomalyLikelihood;
	private transient int learningPhaseCounter;
	private transient DateEncoder dateEncoder;
	
	private PersistenceAPI persistance;

	@Override
	protected ConfigurationResult config(Properties properties) {
		ConfigurationResult confResult = super.config(properties);	
		
		minValue = properties.getFloat(MIN_VALUE_PARAMS, MIN_VALUE_DEFAULT);
		maxValue = properties.getFloat(MAX_VALUE_PARAMS, MAX_VALUE_DEFAULT);
		
		timeOfDay = TOD_DEFAULT;
		try {
			timeOfDay = properties.getBoolean(TOD_PARAMS, TOD_DEFAULT);
		} catch (ConfigurationException e) {
			confResult.withError(null, e);
		}
		dateOfWeek = DOW_DEFAULT;
		try {
			dateOfWeek = properties.getBoolean(DOW_PARAMS, DOW_DEFAULT);
		} catch (ConfigurationException e) {
			confResult.withError(null, e);
		}
		isWeekend = WEEKEND_DEFAULT;
		try {
			isWeekend = properties.getBoolean(WEEKEND_PARAMS, WEEKEND_DEFAULT);
		} catch (ConfigurationException e) {
			confResult.withError(null, e);
		}
		
		errorThreshold = properties.getFloat(ERROR_THRESHOLD_PARAMS, ERROR_THRESHOLD_DEFAULT);
		warningThreshold = properties.getFloat(WARNING_THRESHOLD_PARAMS, WARNING_THRESHOLD_DEFAULT);
		if(errorThreshold < warningThreshold)
			confResult.withError(ERROR_THRESHOLD_PARAMS, "Error Threshold is lower than the warning treshold");
		else {
			anomalyLikelihood = initAnomalyLikelihood(HTMParameters.getAnomalyLikelihoodParams());
			network = buildNetwork();
		}
		learningPhaseCounter = 0;
		
		return confResult.merge(null, properties.warningsIfNotAllPropertiesUsed());
	}
	
	@Override
	public void load(StatusValue store) {
		if(store != null && (store instanceof Status_)) {
			Status_ status_ = ((Status_) store);
			
			network = (Network) byteToPersistable(Base64.decodeBase64(status_.networkBase64));
			anomalyLikelihood = (AnomalyLikelihood) byteToPersistable(Base64.decodeBase64(status_.anomalyLikelihoodBase64));
			learningPhaseCounter = status_.learningPhaseCounter;
			
			network.restart();
		}
		
		if(network == null)
			network = buildNetwork();
	}

	@Override
	public StatusValue save() {
        Status_ status = new Status_();
        status.networkBase64 = Base64.encodeBase64String(persistableToByte(network));
        status.anomalyLikelihoodBase64 = Base64.encodeBase64String(persistableToByte(anomalyLikelihood));
        status.learningPhaseCounter = learningPhaseCounter;
        
        return status;
	}
	
	private byte[] persistableToByte(Persistable pers) {
		if(persistance == null)
			persistance = Persistence.get();
		
		pers.preSerialize();
        byte[] barray = persistance.serializer().serialize(pers);
        return barray;
	}
	
	private Persistable byteToPersistable(byte[] barray) {
	    if(persistance == null)
            persistance = Persistence.get();
	    
		Persistable pers = persistance.read(barray);
		return pers;
	}

	@Override
	public AnalysisResult process(Instant timestamp, double value) {
		AnalysisResult results = new AnalysisResult();
		double likelihood;
		
		Map<String, Object> m = new HashMap<>();
		m.put("timestamp", dateEncoder.parse(timestamp.toString()));
		m.put("value", value);
		Inference i = network.computeImmediate(m);
		learningPhaseCounter = isLearningPhase() ? learningPhaseCounter+1 : -1;
		if(i == null)
			results.setStatus(Status.EXCEPTION, "Inference is null");
		else if(isLearningPhase()) {
			anomalyLikelihood.anomalyProbability((double)m.get("value"), i.getAnomalyScore(),dateEncoder.parse(timestamp.toString()));
			results.setStatus(Status.EXCEPTION, "Algorithm is in the learning phase");
		} else {
			likelihood = anomalyLikelihood.anomalyProbability((double)m.get("value"), i.getAnomalyScore(),dateEncoder.parse(timestamp.toString()));
			AnomaliesResults anomaliesResults = new AnomaliesResults(likelihood, errorThreshold, warningThreshold);
			
			if(anomaliesResults.isError())
				results.setStatus(Status.ERROR, "Anomaly likelihood score ("+anomaliesResults.getAnomalyLikelihoodScore()+") "
						+ "is higher than the fixed error threshold ("+anomaliesResults.getErrThreshold()+")");
			else if(anomaliesResults.isWarning())
				results.setStatus(Status.WARNING, "Anomaly likelihood score ("+anomaliesResults.getAnomalyLikelihoodScore()+") "
						+ "is between the warning threshold ("+anomaliesResults.getWarnThreshold()
						+") and the error threshold ("+anomaliesResults.getErrThreshold()+")");
			else
				results.setStatus(Status.OK, "Anomaly likelihood score is lower than any threshold");
			
			results.addAnalysisParam("anomaly.likelihood", anomaliesResults.getAnomalyLikelihoodScore());
			results.addAnalysisParam("anomaly.score", i.getAnomalyScore());
		}
		
		return results;
	}
	
	private boolean isLearningPhase() {
		Map<String, Object> anomalyLikelihoodParams = HTMParameters.getAnomalyLikelihoodParams();
		return  learningPhaseCounter >= 0 && learningPhaseCounter <= 
				((int)anomalyLikelihoodParams.get(KEY_LEARNING_PERIOD) + (int)anomalyLikelihoodParams.get(KEY_ESTIMATION_SAMPLES));
	}

	private Network buildNetwork(){
    	
		HTMParameters networkParams = new HTMParameters();
		networkParams.setModelParameters(minValue, maxValue, timeOfDay, dateOfWeek, isWeekend);
		
    	Network network = Network.create("Demo", networkParams.getParameters())    
    	    .add(Network.createRegion("Region 1")                       
    	    	.add(Network.createLayer("Layer 2/3", networkParams.getParameters())
    	    		.add(new TemporalMemory())                
    	    		.add(new SpatialPooler())
    	    		.add(Anomaly.create())));
    	
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Object>> fieldEncodingMap = 
				(Map<String, Map<String, Object>>) network.getParameters().get(KEY.FIELD_ENCODING_MAP);
    	
    	MultiEncoder me = MultiEncoder.builder()
	            .name("")
	            .build()
	            .addMultipleEncoders(fieldEncodingMap);
		
		network.lookup("Region 1").lookup("Layer 2/3").add(me);
		
		dateEncoder = me.getEncoderOfType(FieldMetaType.DATETIME);
		
    	return network;
	}
	
	private static AnomalyLikelihood initAnomalyLikelihood(Map<String, Object> anomalyParams) {
		
		boolean useMovingAvg = (boolean)anomalyParams.getOrDefault(KEY_USE_MOVING_AVG, false);
        int windowSize = (int)anomalyParams.getOrDefault(KEY_WINDOW_SIZE, -1);
        
        if(useMovingAvg && windowSize < 1) {
            throw new IllegalArgumentException("windowSize must be > 0, when using moving average.");
        }
		
        boolean isWeighted = (boolean)anomalyParams.getOrDefault(KEY_IS_WEIGHTED, false);
        int claLearningPeriod = (int)anomalyParams.getOrDefault(KEY_LEARNING_PERIOD, VALUE_NONE);
        int estimationSamples = (int)anomalyParams.getOrDefault(KEY_ESTIMATION_SAMPLES, VALUE_NONE);
        
		return new AnomalyLikelihood(useMovingAvg, windowSize, isWeighted, claLearningPeriod, estimationSamples);
	}
	
    @ToString
    @ClassNameAlias("anomaly-likelihood")
    public static class Status_ extends StatusValue{
		private static final long serialVersionUID = 1921682817162401606L;
        public String networkBase64;
        public String anomalyLikelihoodBase64;
        public int learningPhaseCounter;
    }
    
	public static class PersistableJsonAdapter implements JsonSerializer<Persistable>, JsonDeserializer<Persistable> {
		
		private PersistenceAPI persistance;

		@Override
		public JsonElement serialize(Persistable status, java.lang.reflect.Type type, JsonSerializationContext context) {
			if(persistance == null)
				persistance = Persistence.get();
			
			status.preSerialize();
            byte[] barray = persistance.serializer().serialize(status);
			return new JsonPrimitive(Base64.encodeBase64String(barray));
		}
		
		@Override
		public Persistable deserialize(JsonElement element, java.lang.reflect.Type type, JsonDeserializationContext context)
				throws JsonParseException {

			if(!element.isJsonPrimitive())
				throw new JsonParseException("Expected JsonPrimitive");

			byte[] bytes = Base64.decodeBase64(element.getAsString());
			
			Persistable status = persistance.read(bytes);
			return status;
		}

	}
}
