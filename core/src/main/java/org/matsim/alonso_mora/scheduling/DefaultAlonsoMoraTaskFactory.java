package org.matsim.alonso_mora.scheduling;

import org.matsim.api.core.v01.network.Link;

public class DefaultAlonsoMoraTaskFactory implements AlonsoMoraTaskFactory {
    @Override
    public WaitForStopTask createWaitForStopTask(double beginTime, double endTime, Link link) {
        return new WaitForStopTaskImpl(beginTime, endTime, link);
    }
}
