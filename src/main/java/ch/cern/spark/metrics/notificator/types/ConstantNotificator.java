package ch.cern.spark.metrics.notificator.types;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.cern.components.RegisterComponent;
import ch.cern.properties.ConfigurationException;
import ch.cern.properties.Properties;
import ch.cern.spark.metrics.notifications.Notification;
import ch.cern.spark.metrics.notificator.Notificator;
import ch.cern.spark.metrics.results.AnalysisResult.Status;
import ch.cern.spark.status.HasStatus;
import ch.cern.spark.status.StatusValue;
import ch.cern.spark.status.storage.ClassNameAlias;
import ch.cern.utils.TimeUtils;
import lombok.ToString;

@ToString
@RegisterComponent("constant")
public class ConstantNotificator extends Notificator implements HasStatus {
    
    private static final long serialVersionUID = -7890231998987060652L;

    private static final String STATUSES_PARAM = "statuses";
    private Set<Status> expectedStatuses;
    
    private static final String PERIOD_PARAM = "period";
    private static Duration PERIOD_DEFAULT = Duration.ofMinutes(15);
    private Duration period = PERIOD_DEFAULT;
    
    private Instant constantlySeenFrom;
    
    private static final String SILENT_PERIOD_PARAM = "silent.period";
    private Duration silentPeriod;
    private Instant lastRaised;

    @Override
    public void config(Properties properties) throws ConfigurationException {
        super.config(properties);
        
        expectedStatuses = Stream.of(properties.getProperty(STATUSES_PARAM).split("\\s"))
					        		.map(String::trim)
					        		.map(String::toUpperCase)
					        		.map(Status::valueOf)
					        		.collect(Collectors.toSet());

        period = properties.getPeriod(PERIOD_PARAM, PERIOD_DEFAULT);
        
        silentPeriod = properties.getPeriod(SILENT_PERIOD_PARAM, Duration.ofSeconds(0));
        
        properties.confirmAllPropertiesUsed();
    }
    
    @Override
    public void load(StatusValue store) {
        if(store == null || !(store instanceof Status_))
            return;
        
        Status_ data = (Status_) store;
        
        constantlySeenFrom = data.constantlySeenFrom;
        lastRaised = data.lastRaised;
    }

    @Override
    public StatusValue save() {
        Status_ store = new Status_();
        
        store.constantlySeenFrom = constantlySeenFrom;
        store.lastRaised = lastRaised;
        
        return store;
    }

    @Override
    public Optional<Notification> process(Status status, Instant timestamp) {
    		if(lastRaised != null && lastRaised.plus(silentPeriod).compareTo(timestamp) > 0)
    			return Optional.empty();
    		else
    			lastRaised = null;
    	
        boolean isExpectedStatus = isExpectedStatus(status);
        
        if(isExpectedStatus && constantlySeenFrom == null)
            constantlySeenFrom = timestamp;
        
        if(!isExpectedStatus)
            constantlySeenFrom = null;
        
        if(raise(timestamp)){
            Notification notification = new Notification();
            notification.setReason("Metric has been in state " 
                    + expectedStatuses + " for " + TimeUtils.toString(getDiff(timestamp))
                    + ".");
            
            constantlySeenFrom = null;
            lastRaised = timestamp;
            
            return Optional.of(notification);
        }else{
            return Optional.empty();
        }
    }
    
    private boolean isExpectedStatus(Status status) {
        return expectedStatuses.contains(status);
    }
    
    private Duration getDiff(Instant timestamp){
        if(constantlySeenFrom == null)
            return Duration.ZERO;
        
        return Duration.between(constantlySeenFrom, timestamp).abs();
    }

    private boolean raise(Instant timestamp) {
        return getDiff(timestamp).compareTo(period) >= 0;
    }

    @ToString
    @ClassNameAlias("constant-notificator")
    public static class Status_ extends StatusValue{
        private static final long serialVersionUID = -1907347033980904180L;
        
        Instant constantlySeenFrom;
        Instant lastRaised;
    }

}
