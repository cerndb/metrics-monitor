package ch.cern.spark.metrics.monitors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.spark.streaming.State;
import org.apache.spark.streaming.StateImpl;
import org.junit.Test;

import ch.cern.Cache;
import ch.cern.properties.Properties;
import ch.cern.spark.metrics.Metric;
import ch.cern.spark.metrics.results.AnalysisResult;
import ch.cern.spark.metrics.store.Store;

public class MonitorTest {
	
	@Test
	public void shouldUpdateMonitors() throws Exception {
		Monitors.initCache(null);
		Cache<Map<String, Monitor>> monitorsCache = Monitors.getCache();
		
		Properties.getCache().reset();
		monitorsCache.setExpiration(Duration.ofSeconds(1));
		
		//First load
		Map<String, Monitor> originalMonitors = monitorsCache.get();
		
		Map<String, Monitor> returnedMonitors = monitorsCache.get();
		assertSame(originalMonitors, returnedMonitors);
		
		Thread.sleep(Duration.ofMillis(100).toMillis());
		
		returnedMonitors = monitorsCache.get();
		assertSame(originalMonitors, returnedMonitors);
		
		Thread.sleep(Duration.ofSeconds(1).toMillis());
		
		returnedMonitors = monitorsCache.get();
		assertNotSame(originalMonitors, returnedMonitors);
	}

	@Test
	public void tagsShouldBePropagated() throws Exception {
		
		Properties properties = new Properties();
		properties.setProperty("analysis.type", "fixed-threshold");
		properties.setProperty("analysis.error.upperbound", "20");
		properties.setProperty("analysis.error.lowerbound", "10");
		properties.setProperty("tags.email", "1234@cern.ch");
		properties.setProperty("tags.group", "IT_DB");
		properties.setProperty("tags.group", "IT_DB");
		Monitor monitor = new Monitor("test").config(properties);
		
		State<Store> store = new StateImpl<>();
		
		AnalysisResult result = monitor.process(store, new Metric(Instant.now(), 0f, new HashMap<>())).get();
		assertEquals(AnalysisResult.Status.ERROR, result.getStatus());
		assertEquals("1234@cern.ch", result.getTags().get("email"));
		assertEquals("IT_DB", result.getTags().get("group"));
		
		result = monitor.process(store, new Metric(Instant.now(), 15f, new HashMap<>())).get();
		assertEquals(AnalysisResult.Status.OK, result.getStatus());
		assertEquals("1234@cern.ch", result.getTags().get("email"));
		assertEquals("IT_DB", result.getTags().get("group"));
	}

}
