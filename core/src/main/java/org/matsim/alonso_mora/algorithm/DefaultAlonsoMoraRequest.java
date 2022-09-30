package org.matsim.alonso_mora.algorithm;

import java.util.*;
import java.util.stream.Collectors;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup;
import org.matsim.alonso_mora.AlonsoMoraOptimizer;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.schedule.DrtStopTask;
import org.matsim.contrib.dvrp.schedule.Task.TaskStatus;

import com.google.common.base.Verify;

/**
 * This class represents a request in the context of the dispatching algorithm
 * of Alonso-Mora et al. It has multiple purposes. (1) It holds static
 * information such as the origin and destination of a request, as well as time
 * constraints. (2) It represents already an aggregated constraint in case where
 * multiple requests have the same origin and destination. (3) It holds
 * information to which vehicle a request has been assigned. This includes the
 * DVRP tasks for pickup and dropoff.
 * 
 * @author sebhoerl
 */
public class DefaultAlonsoMoraRequest implements AlonsoMoraRequest {
	private final List<DrtRequest> drtRequests;

	private DrtStopTask pickupTask;
	private DrtStopTask dropoffTask;

	private double latestAssignmentTime;

	private AlonsoMoraVehicle vehicle;

	private double plannedPickupTime = Double.NaN;
	private final double directArrivalTime;

	private final int cachedHashCode;

	private final Link pickupLink;
	private final Link dropoffLink;
	private double latestPickupTime;
	private double latestDropoffTime;
	private double earliestPickupTime;

	private final double directRideDistance;

	private final boolean prebooked;

	static Random random = new Random(1);

	public DefaultAlonsoMoraRequest(Collection<DrtRequest> drtRequests, double latestAssignmentTime,
			double directArrivalTime, double directRideDistance, boolean isPrebooked) {
		this.directArrivalTime = directArrivalTime;
		this.directRideDistance = directRideDistance;

		this.drtRequests = new ArrayList<>(drtRequests);
		Collections.sort(this.drtRequests, (a, b) -> Integer.compare(a.getId().index(), b.getId().index()));

		{
			/*
			 * We need a unique has code for the aggregated requests.
			 */
			int hashCode = 13;

			for (int i = 0; i < this.drtRequests.size(); i++) {
				DrtRequest drtRquest = this.drtRequests.get(i);
				hashCode += 27 * (i + 1) * drtRquest.getId().index();
			}

			this.cachedHashCode = hashCode;
		}

		this.pickupLink = this.drtRequests.get(0).getFromLink();
		this.dropoffLink = this.drtRequests.get(0).getToLink();

		this.latestPickupTime = this.drtRequests.stream().mapToDouble(r -> r.getLatestStartTime()).min().getAsDouble();
		this.latestDropoffTime = this.drtRequests.stream().mapToDouble(r -> r.getLatestArrivalTime()).min()
				.getAsDouble();
		this.earliestPickupTime = this.drtRequests.stream().mapToDouble(r -> r.getEarliestStartTime()).max()
				.getAsDouble() - 1800;

		for (DrtRequest request : this.drtRequests) {
			Verify.verify(this.pickupLink.equals(request.getFromLink()));
			Verify.verify(this.dropoffLink.equals(request.getToLink()));
		}

		this.latestAssignmentTime = Math.min(getLatestPickupTime(), latestAssignmentTime);

		//changes for time windows
		double r = random.nextDouble();

		if (AlonsoMoraConfigGroup.getPreOPT().equals("push")) {
			this.latestAssignmentTime = earliestPickupTime + 1800;
			this.latestPickupTime = earliestPickupTime + 1800;
			if (AlonsoMoraConfigGroup.isBigTimeWindows()) {
				this.latestDropoffTime = this.latestDropoffTime + 1800;
			}
		} else if (AlonsoMoraConfigGroup.getPreOPT().equals("window")) {
			double[] split = AlonsoMoraConfigGroup.getSplit();
			if (r < split[0]) {
				this.latestAssignmentTime = earliestPickupTime + 600;
				this.latestPickupTime = earliestPickupTime + 600;
				if (AlonsoMoraConfigGroup.isBigTimeWindows()) {
					this.latestDropoffTime = this.latestDropoffTime + 600;
				}
				AlonsoMoraOptimizer.splitMap.put(this.drtRequests.get(0).getPassengerId(),600);
			} else if (r < split[0] + split[1]) {
				this.latestAssignmentTime = earliestPickupTime + 1200;
				this.latestPickupTime = earliestPickupTime + 1200;
				if (AlonsoMoraConfigGroup.isBigTimeWindows()) {
					this.latestDropoffTime = this.latestDropoffTime + 1200;
				}
				AlonsoMoraOptimizer.splitMap.put(this.drtRequests.get(0).getPassengerId(),1200);
			} else {
				this.latestAssignmentTime = earliestPickupTime + 1800;
				this.latestPickupTime = earliestPickupTime + 1800;
				if (AlonsoMoraConfigGroup.isBigTimeWindows()) {
					this.latestDropoffTime = this.latestDropoffTime + 1800;
				}
				AlonsoMoraOptimizer.splitMap.put(this.drtRequests.get(0).getPassengerId(),1800);
			}
		}
		this.prebooked = isPrebooked;
	}

	@Override
	public int compareTo(AlonsoMoraRequest otherRequest) {
		if (otherRequest instanceof DefaultAlonsoMoraRequest) {
			int sizeComparison = Integer.compare(getSize(), otherRequest.getSize());

			if (sizeComparison != 0) {
				return sizeComparison;
			}

			DefaultAlonsoMoraRequest otherDefaultRequest = (DefaultAlonsoMoraRequest) otherRequest;

			for (int i = 0; i < getSize(); i++) {
				int indexComparison = Integer.compare(drtRequests.get(i).getId().index(),
						otherDefaultRequest.drtRequests.get(i).getId().index());

				if (indexComparison != 0) {
					return indexComparison;
				}
			}

			return 0;
		}

		throw new IllegalStateException();
	}

	@Override
	public boolean equals(Object otherObject) {
		if (otherObject instanceof DefaultAlonsoMoraRequest) {
			DefaultAlonsoMoraRequest otherRequest = (DefaultAlonsoMoraRequest) otherObject;
			return compareTo(otherRequest) == 0;
		}

		throw new IllegalStateException("Cannot compare against unknown object");
	}

	@Override
	public int hashCode() {
		return cachedHashCode;
	}

	public void setPickupTask(AlonsoMoraVehicle vehicle, DrtStopTask pickupTask) {
		Verify.verify(!isPickedUp());
		Verify.verify(!isDroppedOff());

		Verify.verifyNotNull(pickupTask);
		Verify.verifyNotNull(vehicle);

		this.pickupTask = pickupTask;
		this.vehicle = vehicle;
	}

	public void setDropoffTask(AlonsoMoraVehicle vehicle, DrtStopTask dropoffTask) {
		Verify.verify(!isDroppedOff());
		Verify.verify(vehicle == this.vehicle);

		Verify.verifyNotNull(dropoffTask);
		this.dropoffTask = dropoffTask;
	}

	public void unassign() {
		Verify.verify(!isPickedUp());

		this.pickupTask = null;
		this.dropoffTask = null;
		this.vehicle = null;
	}

	public boolean isPickedUp() {
		return pickupTask != null && !pickupTask.getStatus().equals(TaskStatus.PLANNED);
	}

	public boolean isDroppedOff() {
		return dropoffTask != null && !dropoffTask.getStatus().equals(TaskStatus.PLANNED);
	}

	public double getLatestAssignmentTime() {
		return latestAssignmentTime;
	}

	public Collection<DrtRequest> getDrtRequests() {
		return drtRequests;
	}

	@Override
	public void setVehicle(AlonsoMoraVehicle vehicle) {
		this.vehicle = vehicle;
	}

	@Override
	public AlonsoMoraVehicle getVehicle() {
		return vehicle;
	}

	@Override
	public DrtStopTask getPickupTask() {
		return pickupTask;
	}

	@Override
	public DrtStopTask getDropoffTask() {
		return dropoffTask;
	}

	@Override
	public boolean isAssigned() {
		return this.vehicle != null;
	}

	//TODO add new attr to check if prebooked
	@Override
	public boolean isPrebooked() {
		return this.prebooked;
	}

	@Override
	public int getSize() {
		return drtRequests.size();
	}

	@Override
	public Link getPickupLink() {
		return pickupLink;
	}

	@Override
	public Link getDropoffLink() {
		return dropoffLink;
	}

	@Override
	public double getLatestPickupTime() {
		return latestPickupTime;
	}

	@Override
	public double getLatestDropoffTime() {
		return latestDropoffTime;
	}

	@Override
	public double getDirectArivalTime() {
		return directArrivalTime;
	}

	@Override
	public double getPlannedPickupTime() {
		if (Double.isNaN(plannedPickupTime)) {
			return latestPickupTime;
		} else {
			return plannedPickupTime;
		}
	}

	/**
	 * Sets the planned pickup time. This only happens once on the first assignment,
	 * afterwards the pickup time has been promised and can not be changed again.
	 */
	@Override
	public void setPlannedPickupTime(double plannedPickupTime) {
		if (Double.isNaN(this.plannedPickupTime)) {
			this.plannedPickupTime = plannedPickupTime;
		}
	}

	@Override
	public double getDirectRideDistance() {
		return directRideDistance;
	}

	@Override
	public double getEarliestPickupTime() {
		return earliestPickupTime;
	}

	@Override
	public String toString() {
		return "{" + drtRequests.stream().map(r -> r.getId().toString()).collect(Collectors.joining(",")) + "}";
	}
}
