package org.matsim.alonso_mora.scheduling;

import com.google.common.base.MoreObjects;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.schedule.DrtTaskType;
import org.matsim.contrib.dvrp.schedule.DefaultStayTask;
import org.matsim.contrib.evrp.ETask;

import static org.matsim.contrib.drt.schedule.DrtTaskBaseType.STAY;

public class EWaitForStopTask extends DefaultStayTask implements WaitForStopTask, ETask {

    public static final DrtTaskType TYPE = new DrtTaskType("EWaitForStop", STAY);
    private final double consumedEnergy;

    public EWaitForStopTask(double beginTime, double endTime, Link link, double consumedEnergy) {
        super(TYPE, beginTime, endTime, link);
        this.consumedEnergy = consumedEnergy;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("super", super.toString()).toString();
    }

    @Override
    public double getTotalEnergy() {
        return this.consumedEnergy;
    }
}
