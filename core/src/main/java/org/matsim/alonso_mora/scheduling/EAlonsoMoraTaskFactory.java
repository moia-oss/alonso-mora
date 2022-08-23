package org.matsim.alonso_mora.scheduling;

import org.matsim.api.core.v01.network.Link;

public class EAlonsoMoraTaskFactory implements AlonsoMoraTaskFactory {
    @Override
    public WaitForStopTask createWaitForStopTask(double beginTime, double endTime, Link link) {
        return new EWaitForStopTask(beginTime, endTime, link, 0.);
    }
}
