package org.matsim.alonso_mora.algorithm;

import java.util.Collection;

import org.matsim.contrib.drt.passenger.DrtRequest;

/**
 * Default implementation for creating a request for the algorithm by
 * Alonso-Mora et al. from a {#link DrtRequest}. It covers setting the maximum
 * allowable queue time for this request.
 * 
 * @author sebhoerl
 */
public class DefaultAlonsoMoraRequestFactory implements AlonsoMoraRequestFactory {
	private final double maximumQueueTime;

	private final double prebookingHorizon;

	public DefaultAlonsoMoraRequestFactory(double maximumQueueTime, double prebookingHorizon) {
		this.maximumQueueTime = maximumQueueTime;
		this.prebookingHorizon = prebookingHorizon;
	}

	@Override
	public AlonsoMoraRequest createRequest(Collection<DrtRequest> requests, double directArrvialTime, double earliestDepartureTime,
			double directRideDistance, double now) {
		double latestAssignmentTime = earliestDepartureTime + maximumQueueTime;
		double latestPickupTime = requests.stream().mapToDouble(r -> r.getLatestStartTime()).min()
				.orElse(Double.POSITIVE_INFINITY);
		latestAssignmentTime = Math.min(latestAssignmentTime, latestPickupTime);
		double earliestPickupTime = requests.stream().mapToDouble(DrtRequest::getEarliestStartTime).max().getAsDouble();
		boolean isPrebooked = now < earliestPickupTime - prebookingHorizon;
		return new DefaultAlonsoMoraRequest(requests, latestAssignmentTime, directArrvialTime, directRideDistance, isPrebooked);
	}
}
